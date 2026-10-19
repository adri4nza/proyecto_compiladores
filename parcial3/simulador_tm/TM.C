/****************************************************/
/* File: tm.c                                       */
/* The TM ("Tiny Machine") computer                 */
/* Compiler Construction: Principles and Practice   */
/* Kenneth C. Louden                                */
/*                                                   */
/* MODULO 3 — Simulador TM interactivo               */
/* Extendido para el proyecto Tiny++ (UNET):         */
/*   - Modo servidor (-s): lee comandos por stdin y  */
/*     responde una linea JSON por stdout siguiendo  */
/*     el Contrato A (ver estado_tm_mock.json).       */
/*   - El modo interactivo original de Louden se      */
/*     mantiene intacto (sin el flag -s).             */
/*                                                   */
/* NOTA: el unico cambio funcional al modo original   */
/* fue reemplazar gets() por una lectura segura con   */
/* fgets(), porque gets() fue eliminado de glibc y el */
/* archivo original NO compila en Ubuntu/WSL moderno. */
/****************************************************/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

#ifndef TRUE
#define TRUE 1
#endif
#ifndef FALSE
#define FALSE 0
#endif

/******* const *******/
#define   IADDR_SIZE  1024 /* increase for large programs */
#define   DADDR_SIZE  1024 /* increase for large programs */
#define   NO_REGS 8
#define   PC_REG  7

#define   LINESIZE  121
#define   WORDSIZE  20
#define   COMMENT_SIZE 160  /* MODULO 3: tamano del comentario por instruccion */
#define   CMDSIZE   300     /* MODULO 3: tamano de linea de comando del modo servidor */

/******* type  *******/

typedef enum {
   opclRR,     /* reg operands r,s,t */
   opclRM,     /* reg r, mem d+s */
   opclRA      /* reg r, int d+s */
   } OPCLASS;

typedef enum {
   /* RR instructions */
   opHALT,    /* RR     halt, operands are ignored */
   opIN,      /* RR     read into reg(r); s and t are ignored */
   opOUT,     /* RR     write from reg(r), s and t are ignored */
   opADD,    /* RR     reg(r) = reg(s)+reg(t) */
   opSUB,    /* RR     reg(r) = reg(s)-reg(t) */
   opMUL,    /* RR     reg(r) = reg(s)*reg(t) */
   opDIV,    /* RR     reg(r) = reg(s)/reg(t) */
   opRRLim,   /* limit of RR opcodes */

   /* RM instructions */
   opLD,      /* RM     reg(r) = mem(d+reg(s)) */
   opST,      /* RM     mem(d+reg(s)) = reg(r) */
   opRMLim,   /* Limit of RM opcodes */

   /* RA instructions */
   opLDA,     /* RA     reg(r) = d+reg(s) */
   opLDC,     /* RA     reg(r) = d ; reg(s) is ignored */
   opJLT,     /* RA     if reg(r)<0 then reg(7) = d+reg(s) */
   opJLE,     /* RA     if reg(r)<=0 then reg(7) = d+reg(s) */
   opJGT,     /* RA     if reg(r)>0 then reg(7) = d+reg(s) */
   opJGE,     /* RA     if reg(r)>=0 then reg(7) = d+reg(s) */
   opJEQ,     /* RA     if reg(r)==0 then reg(7) = d+reg(s) */
   opJNE,     /* RA     if reg(r)!=0 then reg(7) = d+reg(s) */
   opRALim    /* Limit of RA opcodes */
   } OPCODE;

typedef enum {
   srOKAY,
   srHALT,
   srIMEM_ERR,
   srDMEM_ERR,
   srZERODIVIDE
   } STEPRESULT;

typedef struct {
      int iop  ;
      int iarg1  ;
      int iarg2  ;
      int iarg3  ;
      char comentario[COMMENT_SIZE]; /* MODULO 3: texto libre tras los argumentos en el .tm */
   } INSTRUCTION;

/******** vars ********/
int iloc = 0 ;
int dloc = 0 ;
int traceflag = FALSE;
int icountflag = FALSE;

INSTRUCTION iMem [IADDR_SIZE];
int dMem [DADDR_SIZE];
int reg [NO_REGS];

char * opCodeTab[]
        = {"HALT","IN","OUT","ADD","SUB","MUL","DIV","????",
            /* RR opcodes */
           "LD","ST","????", /* RM opcodes */
           "LDA","LDC","JLT","JLE","JGT","JGE","JEQ","JNE","????"
           /* RA opcodes */
          };

char * stepResultTab[]
        = {"OK","Halted","Instruction Memory Fault",
           "Data Memory Fault","Division by 0"
          };

char pgmName[256];
FILE *pgm  ;

char in_Line[LINESIZE] ;
int lineLen ;
int inCol  ;
int num  ;
char word[WORDSIZE] ;
char ch  ;
int done  ;

/******** MODULO 3: estado del modo servidor ********/
int serverMode        = 0;     /* 1 si se invoco con -s */
int programLoaded      = 0;     /* 1 si ya se hizo 'load' con exito */
int progLen            = 0;     /* cantidad de instrucciones cargadas (para total_instrucciones) */
int breakpointSet[IADDR_SIZE];  /* 1 = hay breakpoint en esa linea */
int lastExecLoc         = -1;    /* loc de la ultima instruccion ejecutada (para instruccion_ejecutada) */
int waitingForInput     = 0;     /* 1 = la TM esta parada en un IN esperando valor */
int pendingInputReg     = -1;    /* registro destino del IN pendiente */
int pendingRunAfterInput = 0;    /* 1 = al llegar el input, retomar el ciclo 'run' */
int hadOutput            = 0;     /* 1 = la ultima instruccion ejecutada fue OUT */
int lastOutputValue      = 0;     /* valor impreso por el ultimo OUT */
char currentEstado[20]     = "detenido";
char currentResultado[20]  = "OK";

/********************************************/
int opClass( int c )
{ if      ( c <= opRRLim) return ( opclRR );
  else if ( c <= opRMLim) return ( opclRM );
  else                    return ( opclRA );
} /* opClass */

/********************************************/
void writeInstruction ( int loc )
{ printf( "%5d: ", loc) ;
  if ( (loc >= 0) && (loc < IADDR_SIZE) )
  { printf("%6s%3d,", opCodeTab[iMem[loc].iop], iMem[loc].iarg1);
    switch ( opClass(iMem[loc].iop) )
    { case opclRR: printf("%1d,%1d", iMem[loc].iarg2, iMem[loc].iarg3);
                   break;
      case opclRM:
      case opclRA: printf("%3d(%1d)", iMem[loc].iarg2, iMem[loc].iarg3);
                   break;
    }
    printf ("\n") ;
  }
} /* writeInstruction */

/********************************************/
/* MODULO 3: lectura segura de una linea desde stdin.                 */
/* Sustituye a gets(), eliminada de glibc moderna (no compilaba en    */
/* Ubuntu/WSL recientes). Solo se usa en el modo interactivo clasico. */
void readLineStdin(char *buf, int size)
{ int len;
  if (fgets(buf, size, stdin) == NULL) { buf[0] = '\0'; return; }
  len = strlen(buf);
  if (len > 0 && buf[len-1] == '\n') buf[len-1] = '\0';
} /* readLineStdin */

/********************************************/
void getCh (void)
{ if (++inCol < lineLen)
  ch = in_Line[inCol] ;
  else ch = ' ' ;
} /* getCh */

/********************************************/
int nonBlank (void)
{ while ((inCol < lineLen)
         && (in_Line[inCol] == ' ') )
    inCol++ ;
  if (inCol < lineLen)
  { ch = in_Line[inCol] ;
    return TRUE ; }
  else
  { ch = ' ' ;
    return FALSE ; }
} /* nonBlank */

/********************************************/
int getNum (void)
{ int sign;
  int term;
  int temp = FALSE;
  num = 0 ;
  do
  { sign = 1;
    while ( nonBlank() && ((ch == '+') || (ch == '-')) )
    { temp = FALSE ;
      if (ch == '-')  sign = - sign ;
      getCh();
    }
    term = 0 ;
    nonBlank();
    while (isdigit(ch))
    { temp = TRUE ;
      term = term * 10 + ( ch - '0' ) ;
      getCh();
    }
    num = num + (term * sign) ;
  } while ( (nonBlank()) && ((ch == '+') || (ch == '-')) ) ;
  return temp;
} /* getNum */

/********************************************/
int getWord (void)
{ int temp = FALSE;
  int length = 0;
  if (nonBlank ())
  { while (isalnum(ch))
    { if (length < WORDSIZE-1) word [length++] =  ch ;
      getCh() ;
    }
    word[length] = '\0';
    temp = (length != 0);
  }
  return temp;
} /* getWord */

/********************************************/
int skipCh ( char c  )
{ int temp = FALSE;
  if ( nonBlank() && (ch == c) )
  { getCh();
    temp = TRUE;
  }
  return temp;
} /* skipCh */

/********************************************/
int atEOL(void)
{ return ( ! nonBlank ());
} /* atEOL */

/********************************************/
int error( char * msg, int lineNo, int instNo)
{ printf("Line %d",lineNo);
  if (instNo >= 0) printf(" (Instruction %d)",instNo);
  printf("   %s\n",msg);
  return FALSE;
} /* error */

/********************************************/
int readInstructions (void)
{ OPCODE op;
  int arg1, arg2, arg3;
  int loc, regNo, lineNo;
  progLen = 0 ; /* MODULO 3 */
  for (regNo = 0 ; regNo < NO_REGS ; regNo++)
      reg[regNo] = 0 ;
  dMem[0] = DADDR_SIZE - 1 ;
  for (loc = 1 ; loc < DADDR_SIZE ; loc++)
      dMem[loc] = 0 ;
  for (loc = 0 ; loc < IADDR_SIZE ; loc++)
  { iMem[loc].iop = opHALT ;
    iMem[loc].iarg1 = 0 ;
    iMem[loc].iarg2 = 0 ;
    iMem[loc].iarg3 = 0 ;
    iMem[loc].comentario[0] = '\0' ; /* MODULO 3 */
  }
  lineNo = 0 ;
  while (! feof(pgm))
  { fgets( in_Line, LINESIZE-2, pgm  ) ;
    inCol = 0 ;
    lineNo++;
    lineLen = strlen(in_Line)-1 ;
    if (in_Line[lineLen]=='\n') in_Line[lineLen] = '\0' ;
    else in_Line[++lineLen] = '\0';
    if ( (nonBlank()) && (in_Line[inCol] != '*') )
    { if (! getNum())
        return error("Bad location", lineNo,-1);
      loc = num;
      if (loc > IADDR_SIZE)
        return error("Location too large",lineNo,loc);
      if (! skipCh(':'))
        return error("Missing colon", lineNo,loc);
      if (! getWord ())
        return error("Missing opcode", lineNo,loc);
      op = opHALT ;
      while ((op < opRALim)
             && (strncmp(opCodeTab[op], word, 4) != 0) )
          op++ ;
      if (strncmp(opCodeTab[op], word, 4) != 0)
          return error("Illegal opcode", lineNo,loc);
      switch ( opClass(op) )
      { case opclRR :
        /***********************************/
        if ( (! getNum ()) || (num < 0) || (num >= NO_REGS) )
            return error("Bad first register", lineNo,loc);
        arg1 = num;
        if ( ! skipCh(','))
            return error("Missing comma", lineNo, loc);
        if ( (! getNum ()) || (num < 0) || (num >= NO_REGS) )
            return error("Bad second register", lineNo, loc);
        arg2 = num;
        if ( ! skipCh(','))
            return error("Missing comma", lineNo,loc);
        if ( (! getNum ()) || (num < 0) || (num >= NO_REGS) )
            return error("Bad third register", lineNo,loc);
        arg3 = num;
        break;

        case opclRM :
        case opclRA :
        /***********************************/
        if ( (! getNum ()) || (num < 0) || (num >= NO_REGS) )
            return error("Bad first register", lineNo,loc);
        arg1 = num;
        if ( ! skipCh(','))
            return error("Missing comma", lineNo,loc);
        if (! getNum ())
            return error("Bad displacement", lineNo,loc);
        arg2 = num;
        if ( ! skipCh('(') && ! skipCh(',') )
            return error("Missing LParen", lineNo,loc);
        if ( (! getNum ()) || (num < 0) || (num >= NO_REGS))
            return error("Bad second register", lineNo,loc);
        arg3 = num;
        skipCh(')'); /* MODULO 3: el Louden original nunca consumia el ')' de cierre; */
                     /* sin esto, el parentesis se filtraba al comentario capturado.  */
        break;
        }
      iMem[loc].iop = op;
      iMem[loc].iarg1 = arg1;
      iMem[loc].iarg2 = arg2;
      iMem[loc].iarg3 = arg3;
      if (loc+1 > progLen) progLen = loc+1 ; /* MODULO 3 */
      /* MODULO 3: lo que quede en la linea tras los argumentos es el  */
      /* comentario (tal como lo emite Generador.java/UtGen.java),    */
      /* opcionalmente precedido por un '*'.                          */
      if (nonBlank())
      { char *src = &in_Line[inCol];
        if (*src == '*') { src++; while (*src == ' ') src++; }
        strncpy(iMem[loc].comentario, src, COMMENT_SIZE-1);
        iMem[loc].comentario[COMMENT_SIZE-1] = '\0';
      }
    }
  }
  return TRUE;
} /* readInstructions */


/********************************************/
STEPRESULT stepTM (void)
{ INSTRUCTION currentinstruction  ;
  int pc  ;
  int r,s,t,m  ;
  int ok ;

  pc = reg[PC_REG] ;
  if ( (pc < 0) || (pc > IADDR_SIZE)  )
      return srIMEM_ERR ;
  reg[PC_REG] = pc + 1 ;
  currentinstruction = iMem[ pc ] ;
  switch (opClass(currentinstruction.iop) )
  { case opclRR :
    /***********************************/
      r = currentinstruction.iarg1 ;
      s = currentinstruction.iarg2 ;
      t = currentinstruction.iarg3 ;
      break;

    case opclRM :
    /***********************************/
      r = currentinstruction.iarg1 ;
      s = currentinstruction.iarg3 ;
      m = currentinstruction.iarg2 + reg[s] ;
      if ( (m < 0) || (m > DADDR_SIZE))
         return srDMEM_ERR ;
      break;

    case opclRA :
    /***********************************/
      r = currentinstruction.iarg1 ;
      s = currentinstruction.iarg3 ;
      m = currentinstruction.iarg2 + reg[s] ;
      break;
  } /* case */

  switch ( currentinstruction.iop)
  { /* RR instructions */
    case opHALT :
    /***********************************/
      if (!serverMode) printf("HALT: %1d,%1d,%1d\n",r,s,t); /* MODULO 3: en modo -s el HALT se reporta solo via JSON */
      return srHALT ;
      /* break; */

    case opIN :
    /***********************************/
      /* MODULO 3: en modo servidor, IN NUNCA llega a ejecutarse aqui.    */
      /* runServerLoop()/handleStep()/handleRun() detectan opIN ANTES de  */
      /* llamar stepTM() y pausan la maquina para pedir el valor al IDE   */
      /* via el comando 'input <valor>'. Este bloque solo corre en el    */
      /* modo interactivo clasico de terminal.                           */
      do
      { printf("Enter value for IN instruction: ") ;
        fflush (stdin);
        fflush (stdout);
        readLineStdin(in_Line, LINESIZE); /* MODULO 3: antes gets(in_Line) */
        lineLen = strlen(in_Line) ;
        inCol = 0;
        ok = getNum();
        if ( ! ok ) printf ("Illegal value\n");
        else reg[r] = num;
      }
      while (! ok);
      break;

    case opOUT :
      lastOutputValue = reg[r] ; /* MODULO 3: se transporta al IDE via el campo "salida_consola" */
      hadOutput = 1 ;
      if (!serverMode) printf ("OUT instruction prints: %d\n", reg[r] ) ;
      break;
    case opADD :  reg[r] = reg[s] + reg[t] ;  break;
    case opSUB :  reg[r] = reg[s] - reg[t] ;  break;
    case opMUL :  reg[r] = reg[s] * reg[t] ;  break;

    case opDIV :
    /***********************************/
      if ( reg[t] != 0 ) reg[r] = reg[s] / reg[t];
      else return srZERODIVIDE ;
      break;

    /*************** RM instructions ********************/
    case opLD :    reg[r] = dMem[m] ;  break;
    case opST :    dMem[m] = reg[r] ;  break;

    /*************** RA instructions ********************/
    case opLDA :    reg[r] = m ; break;
    case opLDC :    reg[r] = currentinstruction.iarg2 ;   break;
    case opJLT :    if ( reg[r] <  0 ) reg[PC_REG] = m ; break;
    case opJLE :    if ( reg[r] <=  0 ) reg[PC_REG] = m ; break;
    case opJGT :    if ( reg[r] >  0 ) reg[PC_REG] = m ; break;
    case opJGE :    if ( reg[r] >=  0 ) reg[PC_REG] = m ; break;
    case opJEQ :    if ( reg[r] == 0 ) reg[PC_REG] = m ; break;
    case opJNE :    if ( reg[r] != 0 ) reg[PC_REG] = m ; break;

    /* end of legal instructions */
  } /* case */
  return srOKAY ;
} /* stepTM */

/********************************************/
int doCommand (void)
{ char cmd;
  int stepcnt=0, i;
  int printcnt;
  int stepResult;
  int regNo, loc;
  do
  { printf ("Enter command: ");
    fflush (stdin);
    fflush (stdout);
    readLineStdin(in_Line, LINESIZE); /* MODULO 3: antes gets(in_Line) */
    lineLen = strlen(in_Line);
    inCol = 0;
  }
  while (! getWord ());

  cmd = word[0] ;
  switch ( cmd )
  { case 't' :
    /***********************************/
      traceflag = ! traceflag ;
      printf("Tracing now ");
      if ( traceflag ) printf("on.\n"); else printf("off.\n");
      break;

    case 'h' :
    /***********************************/
      printf("Commands are:\n");
      printf("   s(tep <n>      "\
             "Execute n (default 1) TM instructions\n");
      printf("   g(o            "\
             "Execute TM instructions until HALT\n");
      printf("   r(egs          "\
             "Print the contents of the registers\n");
      printf("   i(Mem <b <n>>  "\
             "Print n iMem locations starting at b\n");
      printf("   d(Mem <b <n>>  "\
             "Print n dMem locations starting at b\n");
      printf("   t(race         "\
             "Toggle instruction trace\n");
      printf("   p(rint         "\
             "Toggle print of total instructions executed"\
             " ('go' only)\n");
      printf("   c(lear         "\
             "Reset simulator for new execution of program\n");
      printf("   h(elp          "\
             "Cause this list of commands to be printed\n");
      printf("   q(uit          "\
             "Terminate the simulation\n");
      break;

    case 'p' :
    /***********************************/
      icountflag = ! icountflag ;
      printf("Printing instruction count now ");
      if ( icountflag ) printf("on.\n"); else printf("off.\n");
      break;

    case 's' :
    /***********************************/
      if ( atEOL ())  stepcnt = 1;
      else if ( getNum ())  stepcnt = abs(num);
      else   printf("Step count?\n");
      break;

    case 'g' :   stepcnt = 1 ;     break;

    case 'r' :
    /***********************************/
      for (i = 0; i < NO_REGS; i++)
      { printf("%1d: %4d    ", i,reg[i]);
        if ( (i % 4) == 3 ) printf ("\n");
      }
      break;

    case 'i' :
    /***********************************/
      printcnt = 1 ;
      if ( getNum ())
      { iloc = num ;
        if ( getNum ()) printcnt = num ;
      }
      if ( ! atEOL ())
        printf ("Instruction locations?\n");
      else
      { while ((iloc >= 0) && (iloc < IADDR_SIZE)
                && (printcnt > 0) )
        { writeInstruction(iloc);
          iloc++ ;
          printcnt-- ;
        }
      }
      break;

    case 'd' :
    /***********************************/
      printcnt = 1 ;
      if ( getNum  ())
      { dloc = num ;
        if ( getNum ()) printcnt = num ;
      }
      if ( ! atEOL ())
        printf("Data locations?\n");
      else
      { while ((dloc >= 0) && (dloc < DADDR_SIZE)
                  && (printcnt > 0))
        { printf("%5d: %5d\n",dloc,dMem[dloc]);
          dloc++;
          printcnt--;
        }
      }
      break;

    case 'c' :
    /***********************************/
      iloc = 0;
      dloc = 0;
      stepcnt = 0;
      for (regNo = 0;  regNo < NO_REGS ; regNo++)
            reg[regNo] = 0 ;
      dMem[0] = DADDR_SIZE - 1 ;
      for (loc = 1 ; loc < DADDR_SIZE ; loc++)
            dMem[loc] = 0 ;
      break;

    case 'q' : return FALSE;  /* break; */

    default : printf("Command %c unknown.\n", cmd); break;
  }  /* case */
  stepResult = srOKAY;
  if ( stepcnt > 0 )
  { if ( cmd == 'g' )
    { stepcnt = 0;
      while (stepResult == srOKAY)
      { iloc = reg[PC_REG] ;
        if ( traceflag ) writeInstruction( iloc ) ;
        stepResult = stepTM ();
        stepcnt++;
      }
      if ( icountflag )
        printf("Number of instructions executed = %d\n",stepcnt);
    }
    else
    { while ((stepcnt > 0) && (stepResult == srOKAY))
      { iloc = reg[PC_REG] ;
        if ( traceflag ) writeInstruction( iloc ) ;
        stepResult = stepTM ();
        stepcnt-- ;
      }
    }
    printf( "%s\n",stepResultTab[stepResult] );
  }
  return TRUE;
} /* doCommand */


/******************************************************************/
/* MODULO 3 — MODO SERVIDOR (-s)                                  */
/* Todo lo que sigue es nuevo: protocolo stdin/stdout con M4 via   */
/* el servidor_puente (Contratos A y C de 02_INSTRUCCIONES_IA_EQUIPO.md) */
/******************************************************************/

/********************************************/
/* Escapa comillas, backslashes y saltos de linea para JSON valido */
void printJSONEscaped(const char *s)
{ const char *p;
  for (p = s; *p != '\0'; p++)
  { if (*p == '"' || *p == '\\') { putchar('\\'); putchar(*p); }
    else if (*p == '\n') printf("\\n");
    else putchar(*p);
  }
} /* printJSONEscaped */

/********************************************/
/* Imprime el estado completo de la TM como UNA linea JSON (Contrato A). */
void printStateJSON(const char *resultado_paso, const char *estado,
                     const char *mensaje_error, int esperando_input)
{ int i, first;
  size_t n;

  /* MODULO 3: ojo — en varias llamadas (handleBreak, handleInput, comando  */
  /* desconocido) "estado"/"resultado_paso" SON currentEstado/currentResultado */
  /* (se reenvia el estado actual sin cambios). strncpy() con origen y destino */
  /* iguales es comportamiento indefinido; memmove() si esta garantizado para */
  /* regiones superpuestas (incluido el caso de puntero identico).           */
  n = strlen(estado);
  if (n > sizeof(currentEstado)-1) n = sizeof(currentEstado)-1;
  memmove(currentEstado, estado, n);
  currentEstado[n] = '\0';

  n = strlen(resultado_paso);
  if (n > sizeof(currentResultado)-1) n = sizeof(currentResultado)-1;
  memmove(currentResultado, resultado_paso, n);
  currentResultado[n] = '\0';

  printf("{");

  /* registros: mapeo fijo reg[0..7] -> AC,AC1,R2,R3,FP,GP,MP,PC */
  printf("\"registros\":{\"PC\":%d,\"AC\":%d,\"AC1\":%d,\"GP\":%d,\"MP\":%d,\"FP\":%d,\"R2\":%d,\"R3\":%d},",
         reg[PC_REG], reg[0], reg[1], reg[5], reg[6], reg[4], reg[2], reg[3]);

  /* memoria_datos: mapa disperso (solo celdas != 0, mas la 0 siempre) */
  printf("\"memoria_datos\":{");
  first = 1;
  for (i = 0; i < DADDR_SIZE; i++)
  { if (i == 0 || dMem[i] != 0)
    { if (!first) printf(",");
      printf("\"%d\":%d", i, dMem[i]);
      first = 0;
    }
  }
  printf(",\"_size\":%d},", DADDR_SIZE);

  /* memoria_instrucciones: el programa completo cargado */
  printf("\"memoria_instrucciones\":[");
  for (i = 0; i < progLen; i++)
  { if (i > 0) printf(",");
    printf("{\"linea\":%d,\"op\":\"%s\",\"r\":%d,\"d\":%d,\"s\":%d,\"comentario\":\"",
           i, opCodeTab[iMem[i].iop], iMem[i].iarg1, iMem[i].iarg2, iMem[i].iarg3);
    printJSONEscaped(iMem[i].comentario);
    printf("\"}");
  }
  printf("],");

  printf("\"total_instrucciones\":%d,", progLen);

  if (lastExecLoc >= 0 && lastExecLoc < progLen)
  { printf("\"instruccion_ejecutada\":{\"linea\":%d,\"op\":\"%s\",\"r\":%d,\"d\":%d,\"s\":%d},",
           lastExecLoc, opCodeTab[iMem[lastExecLoc].iop],
           iMem[lastExecLoc].iarg1, iMem[lastExecLoc].iarg2, iMem[lastExecLoc].iarg3);
  }
  else printf("\"instruccion_ejecutada\":null,");

  printf("\"estado\":\"%s\",", estado);
  printf("\"resultado_paso\":\"%s\",", resultado_paso);

  if (mensaje_error == NULL) printf("\"mensaje_error\":null,");
  else { printf("\"mensaje_error\":\""); printJSONEscaped(mensaje_error); printf("\","); }

  printf("\"esperando_input\":%s,", esperando_input ? "true" : "false");

  /* salida_consola: valor del ultimo OUT ejecutado en ESTE comando;    */
  /* null si no hubo OUT. Vease la ALERTA DE INTEGRACION junto a main. */
  if (hadOutput) printf("\"salida_consola\":%d,", lastOutputValue);
  else printf("\"salida_consola\":null,");

  printf("\"breakpoints\":[");
  first = 1;
  for (i = 0; i < IADDR_SIZE; i++)
  { if (breakpointSet[i])
    { if (!first) printf(",");
      printf("%d", i);
      first = 0;
    }
  }
  printf("]");

  printf("}\n");
  fflush(stdout); /* redundante junto a setbuf(stdout,NULL), pero no estorba */

  hadOutput = 0; /* el OUT se reporta una sola vez, en la respuesta que lo produjo */
} /* printStateJSON */

/********************************************/
/* Traduce un STEPRESULT de stepTM() al par (resultado_paso, estado) del Contrato A */
void reportStepResult(STEPRESULT r)
{ switch (r)
  { case srOKAY:       printStateJSON("OK", "ejecutando", NULL, 0); break;
    case srHALT:       printStateJSON("HALT", "detenido", NULL, 0); break;
    case srIMEM_ERR:   printStateJSON("IMEM_ERR", "error",
                            "PC fuera del rango de instrucciones", 0); break;
    case srDMEM_ERR:   printStateJSON("DMEM_ERR", "error",
                            "Acceso a memoria de datos fuera de rango", 0); break;
    case srZERODIVIDE: printStateJSON("ZERO_DIVIDE", "error",
                            "Division por cero", 0); break;
  }
} /* reportStepResult */

/********************************************/
void handleLoad(char *path)
{ char trimmed[256];
  char *p = path;
  while (*p == ' ') p++;
  strncpy(trimmed, p, sizeof(trimmed)-1);
  trimmed[sizeof(trimmed)-1] = '\0';

  if (pgm != NULL) { fclose(pgm); pgm = NULL; }
  pgm = fopen(trimmed, "r");
  if (pgm == NULL)
  { programLoaded = 0;
    printStateJSON("OK", "error", "No se pudo abrir el archivo .tm especificado", 0);
    return;
  }

  if (! readInstructions())
  { fclose(pgm); pgm = NULL;
    programLoaded = 0;
    printStateJSON("OK", "error", "Archivo .tm invalido (ver formato de instrucciones)", 0);
    return;
  }
  fclose(pgm); pgm = NULL;

  memset(breakpointSet, 0, sizeof(breakpointSet));
  waitingForInput = 0;
  pendingRunAfterInput = 0;
  pendingInputReg = -1;
  lastExecLoc = -1;
  hadOutput = 0;
  programLoaded = 1;

  printStateJSON("OK", "detenido", NULL, 0);
} /* handleLoad */

/********************************************/
void handleReset(void)
{ int i;
  if (! programLoaded)
  { printStateJSON("OK", "error", "No hay programa cargado. Use 'load <ruta>' primero.", 0);
    return;
  }

  for (i = 0; i < NO_REGS; i++) reg[i] = 0;
  dMem[0] = DADDR_SIZE - 1;
  for (i = 1; i < DADDR_SIZE; i++) dMem[i] = 0;

  waitingForInput = 0;
  pendingRunAfterInput = 0;
  pendingInputReg = -1;
  lastExecLoc = -1;
  hadOutput = 0;

  printStateJSON("OK", "detenido", NULL, 0);
} /* handleReset */

/********************************************/
void handleBreak(char *arg)
{ int n = atoi(arg);
  if (! programLoaded)
  { printStateJSON("OK", "error", "No hay programa cargado. Use 'load <ruta>' primero.", 0);
    return;
  }
  if (n < 0 || n >= IADDR_SIZE)
  { printStateJSON(currentResultado, currentEstado, "Numero de linea de breakpoint invalido", waitingForInput);
    return;
  }
  breakpointSet[n] = ! breakpointSet[n];
  /* Toggle de breakpoint: no ejecuta nada, solo reporta el estado actual */
  printStateJSON(currentResultado, currentEstado, NULL, waitingForInput);
} /* handleBreak */

/********************************************/
void handleRun(void); /* forward: handleInput puede retomar un run pausado por IN */

/********************************************/
void handleStep(void)
{ int pc;
  STEPRESULT r;

  if (! programLoaded)
  { printStateJSON("OK", "error", "No hay programa cargado. Use 'load <ruta>' primero.", 0);
    return;
  }
  if (waitingForInput)
  { printStateJSON("OK", "esperando_input",
        "El programa espera un valor de entrada (use 'input <valor>')", 1);
    return;
  }

  pc = reg[PC_REG];
  if (pc < 0 || pc >= IADDR_SIZE)
  { reportStepResult(srIMEM_ERR);
    return;
  }

  if (iMem[pc].iop == opIN)
  { /* Contrato A ampliado: la TM se pausa en el IN y le toca al IDE */
    /* resolverlo graficamente (ver ALERTA DE INTEGRACION junto a main). */
    waitingForInput = 1;
    pendingInputReg = iMem[pc].iarg1;
    pendingRunAfterInput = 0;
    printStateJSON("OK", "esperando_input", NULL, 1);
    return;
  }

  lastExecLoc = pc;
  r = stepTM();
  reportStepResult(r);
} /* handleStep */

/********************************************/
void handleRun(void)
{ int pc, firstIter;
  STEPRESULT r = srOKAY;

  if (! programLoaded)
  { printStateJSON("OK", "error", "No hay programa cargado. Use 'load <ruta>' primero.", 0);
    return;
  }
  if (waitingForInput)
  { printStateJSON("OK", "esperando_input",
        "El programa espera un valor de entrada (use 'input <valor>')", 1);
    return;
  }

  firstIter = 1;
  for ( ; ; )
  { pc = reg[PC_REG];
    if (pc < 0 || pc >= IADDR_SIZE) { r = srIMEM_ERR; break; }

    /* se detiene ANTES de ejecutar una linea marcada, salvo si es la    */
    /* linea desde la que se esta retomando (para poder pasar por ella). */
    if (! firstIter && breakpointSet[pc])
    { printStateJSON("OK", "detenido", NULL, 0);
      return;
    }
    firstIter = 0;

    if (iMem[pc].iop == opIN)
    { waitingForInput = 1;
      pendingInputReg = iMem[pc].iarg1;
      pendingRunAfterInput = 1;
      printStateJSON("OK", "esperando_input", NULL, 1);
      return;
    }

    lastExecLoc = pc;
    r = stepTM();
    if (r != srOKAY) break;
  }
  reportStepResult(r);
} /* handleRun */

/********************************************/
void handleInput(char *valueStr)
{ int value;
  if (! programLoaded)
  { printStateJSON("OK", "error", "No hay programa cargado. Use 'load <ruta>' primero.", 0);
    return;
  }
  if (! waitingForInput)
  { printStateJSON(currentResultado, currentEstado,
        "No se esperaba un valor de entrada (no hay IN pendiente)", 0);
    return;
  }

  value = atoi(valueStr);
  reg[pendingInputReg] = value;
  lastExecLoc = reg[PC_REG];      /* loc de la instruccion IN que se completa ahora */
  reg[PC_REG] = reg[PC_REG] + 1;
  waitingForInput = 0;
  pendingInputReg = -1;

  if (pendingRunAfterInput)
  { pendingRunAfterInput = 0;
    handleRun(); /* retoma el ciclo run justo donde se habia pausado */
  }
  else printStateJSON("OK", "ejecutando", NULL, 0);
} /* handleInput */

/********************************************/
/* Bucle principal del modo servidor: una linea de comando -> una linea JSON */
void runServerLoop(void)
{ char line[CMDSIZE];
  int len;

  while (fgets(line, sizeof(line), stdin) != NULL)
  { len = strlen(line);
    if (len > 0 && line[len-1] == '\n') line[--len] = '\0';

    if      (strncmp(line, "load ", 5) == 0)  handleLoad(line+5);
    else if (strcmp (line, "step")      == 0) handleStep();
    else if (strcmp (line, "run")       == 0) handleRun();
    else if (strcmp (line, "reset")     == 0) handleReset();
    else if (strncmp(line, "break ", 6) == 0) handleBreak(line+6);
    else if (strncmp(line, "input ", 6) == 0) handleInput(line+6);
    else if (len == 0) /* linea vacia: ignorar silenciosamente */ ;
    else
    { printStateJSON(currentResultado, currentEstado, "Comando no reconocido", waitingForInput);
    }
  }
} /* runServerLoop */


/********************************************/
/* E X E C U T I O N   B E G I N S   H E R E */
/********************************************/

int main( int argc, char * argv[] )
{
  /* MODULO 3 / Trampa 3 (obligatoria): sin esto, el JSON queda atrapado */
  /* en el buffer de stdout cuando corre con stdout conectado a un pipe. */
  setbuf(stdout, NULL);

  if (argc >= 2 && strcmp(argv[1], "-s") == 0)
  { /* ---- modo servidor para el IDE (servidor_puente) ---- */
    serverMode = 1;
    memset(breakpointSet, 0, sizeof(breakpointSet));

    if (argc >= 3)
    { /* carga opcional al arrancar: ./tm -s archivo.tm */
      strncpy(pgmName, argv[2], sizeof(pgmName)-1);
      pgmName[sizeof(pgmName)-1] = '\0';
      if (strchr(pgmName, '.') == NULL) strcat(pgmName, ".tm");
      pgm = fopen(pgmName, "r");
      if (pgm != NULL)
      { readInstructions();
        fclose(pgm); pgm = NULL;
        programLoaded = 1;
      }
    }

    runServerLoop();
    return 0;
  }

  /* ---- modo interactivo original de Louden (sin cambios funcionales) ---- */
  if (argc != 2)
  { printf("usage: %s <filename>\n",argv[0]);
    exit(1);
  }
  strcpy(pgmName,argv[1]) ;
  if (strchr (pgmName, '.') == NULL)
     strcat(pgmName,".tm");
  pgm = fopen(pgmName,"r");
  if (pgm == NULL)
  { printf("file '%s' not found\n",pgmName);
    exit(1);
  }

  /* read the program */
  if ( ! readInstructions ())
         exit(1) ;
  /* switch input file to terminal */
  /* reset( input ); */
  /* read-eval-print */
  printf("TM  simulation (enter h for help)...\n");
  do
     done = ! doCommand ();
  while (! done );
  printf("Simulation done.\n");
  return 0;
}

/****************************************************************************
 * ALERTA DE INTEGRACION
 * ------------------------------------------------------------------------
 * Este archivo AMPLIA el Contrato A (estado_tm_mock.json) con una clave
 * nueva: "salida_consola" (entero o null). Es necesaria porque la
 * instruccion OUT debe "resolverse graficamente dentro del IDE, no en
 * terminal externa" (00_CONTEXTO_PROYECTO.md, 4.3) y el contrato original
 * no tenia forma de transportar ese valor al frontend.
 *
 * Tambien AMPLIA el Contrato C (comandos stdin) con un comando nuevo:
 * "input <valor>\n" — se usa para responder un IN cuando la TM queda en
 * estado "esperando_input" (en vez de bloquear leyendo de stdin, que
 * rompería el protocolo de un-comando-por-linea).
 *
 * Impacto: Responsable M4 (servidor_puente / React) debe:
 *   1) Enviar "input <valor>\n" cuando "esperando_input" sea true,
 *      en lugar de "step" o "run".
 *   2) Leer "salida_consola" tras cada step/run/input para mostrar el OUT.
 * Accion requerida: notificar a Responsable M3 y Responsable M4 antes de
 * dar por cerrado el Contrato A/C. Si el equipo ya lo acordo, se puede
 * ignorar esta alerta.
 ****************************************************************************/
