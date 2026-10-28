grammar PokemonLog;

//PARSER

gameLog : NEWLINE* setupBlock turnBlock* EOF ;

setupBlock : SETUP eol setupLine* ;

setupLine
    : coinChoice
    | coinWinner
    | turnOrder
    | detailLine
    | genericLine
    ;

// "DudeJaden chose heads for the opening coin flip."
coinChoice : player=WORD CHOSE side=WORD restOfLine eol ;
// "DudeJaden won the coin toss."
coinWinner : player=WORD WON restOfLine eol ;
// "DudeJaden decided to go first."
turnOrder  : player=WORD DECIDED TO WORD order=WORD DOT eol ;

// "DudeJaden's Turn"
turnBlock : player=POSS TURN eol statement* ;

statement
    : attackLine
    | abilityLine
    | knockoutLine
    | prizeLine
    | endTurnLine
    | playLine
    | timeoutLine
    | winnerLine
    | detailLine
    | genericLine
    ;

// "X's Mega Lucario ex used Aura Jab on Y's Lunatone for 130 damage."
attackLine
    : owner=POSS pokemon=words USED attack=words
      ON targetOwner=POSS target=words
      FOR damage=NUMBER DAMAGE DOT eol ;

// "X's Meowth ex used Last-Ditch Catch."
abilityLine : owner=POSS pokemon=words USED move=words DOT eol ;

// "Y's Makuhita was Knocked Out!"
knockoutLine : owner=POSS pokemon=words WAS KNOCKED OUT BANG eol ;

// "X took a Prize card." / "X took 3 Prize cards."
prizeLine : player=WORD TOOK count=(NUMBER|WORD) PRIZE WORD DOT eol ;

// "X ended their turn."
endTurnLine : player=WORD ENDED THEIR WORD DOT eol ;

// "X played Makuhita to the Active Spot." / "X played Judge."
playLine : player=WORD PLAYED card=words (TO place=words)? DOT eol ;

// "X didn't take an action in time."
timeoutLine : player=WORD DIDNT restOfLine eol ;

// "All Prize cards taken. X wins."  /  "Opponent was inactive for too long. X wins."
winnerLine : (~(DOT | NEWLINE | TURN))* DOT winner=WORD WINS DOT eol ;

// líneas de detalle: "- ..." o "   • ..."
detailLine  : (DASH | BULLET) restOfLine eol ;
genericLine : (~(NEWLINE | TURN))+ eol ;

restOfLine : (~(NEWLINE | TURN))* ;
words      : (WORD | NUMBER | POSS | PRIZE)+ ;
eol : NEWLINE | {_input.LA(1) == EOF}? ;

//LEXER

SETUP   : 'Setup' ;
TURN    : 'Turn' ;
USED    : 'used' ;
ON      : 'on' ;
FOR     : 'for' ;
DAMAGE  : 'damage' ;
WAS     : 'was' ;
KNOCKED : 'Knocked' ;
OUT     : 'Out' ;
WINS    : 'wins' ;
TOOK    : 'took' ;
PRIZE   : 'Prize' ;
ENDED   : 'ended' ;
THEIR   : 'their' ;
PLAYED  : 'played' ;
TO      : 'to' ;
CHOSE   : 'chose' ;
WON     : 'won' ;
DECIDED : 'decided' ;
DIDNT   : 'didn' APOS 't' ;

NUMBER  : [0-9]+ ;
POSS    : WCHAR+ APOS 's' ;                      // DudeJaden's / DrCompiler’s
WORD    : WCHAR+ ('-' WCHAR+)* ;                 // Last-Ditch, User82738, Poké

DOT     : '.' ;
BANG    : '!' ;
DASH    : '-' ;
BULLET  : '•' ;
NEWLINE : [\r\n]+ ;
WS      : [ \t\u00A0]+ -> skip ;
OTHER   : . ;                                    // cualquier símbolo suelto, nunca falla el lexer

fragment WCHAR : [a-zA-Z0-9_\u00C0-\u017F] ;
fragment APOS  : '\'' | '’' ;