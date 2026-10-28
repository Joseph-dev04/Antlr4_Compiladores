grammar PokemonLog;

// REGLAS SINTÁCTICAS

gameLog : setupBlock turnBlock* matchOutcome EOF ;

setupBlock   : SETUP_HEADER NEWLINE setupLine* ;
setupLine    : normalLine ;

turnBlock    : TURN_HEADER NEWLINE turnLine* ;
turnLine     : normalLine ;

matchOutcome : startToken otherToken* NEWLINE? ;

normalLine   : startToken otherToken* NEWLINE ;

startToken   : DASH | BULLET | PLAYER_NAME | WORD | ALL_WORD ;
otherToken   : PLAYER_NAME | WORD | NUM | APOSTROPHE | DASH | BULLET | COMMA | COLON | DOT | LPAREN | RPAREN | ALL_WORD ;


// REGLAS LÉXICAS

SETUP_HEADER : 'Setup' ;
TURN_HEADER  : [a-zA-Z0-9_\u00C0-\u017F]+ ('\'' | '’') 'Turn' ;
ALL_WORD     : 'All' ;

APOSTROPHE   : '\'' | '’' ;
DASH         : '-' ;
BULLET       : '•' | '*' ;
COMMA        : ',' ;
COLON        : ':' ;
DOT          : '.' ;
LPAREN       : '(' ;
RPAREN       : ')' ;

PLAYER_NAME  : [a-zA-Z0-9_\u00C0-\u017F]+ ;
WORD         : [a-zA-Z0-9_\u00C0-\u017F'’!\-%]+ ;
NUM          : [0-9]+ ;

NEWLINE      : [\r\n]+ ;
WS           : [ \t\u00A0]+ -> skip ;