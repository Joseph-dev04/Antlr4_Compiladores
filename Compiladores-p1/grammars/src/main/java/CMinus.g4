grammar CMinus;

// ANALISIS SINTACTICO
// Nivel basico de Louden + ampliaciones del nivel medio.

program: declaration+ EOF
    ;

declaration: varDeclaration
    | funDeclaration
    ;

varDeclaration: typeSpecifier ID ('[' NUM ']')? ';'
    ;

typeSpecifier: INT
    | VOID
    | CHAR
    | BOOL
    ;

funDeclaration: typeSpecifier ID '(' params ')' compoundStmt
    ;

params: paramList
    | VOID
    ;

paramList: param (',' param)*
    ;

param: typeSpecifier ID ('[' ']')?
    ;

compoundStmt: '{' localDeclarations statementList '}'
    ;

localDeclarations: varDeclaration*
    ;

statementList: statement*
    ;

statement: expressionStmt
    | compoundStmt
    | selectionStmt
    | iterationStmt
    | returnStmt
    ;

expressionStmt: expression? ';'
    ;

selectionStmt: IF '(' expression ')' statement (ELSE statement)?
    ;

// En for las tres partes son opcionales; los dos ';' son obligatorios.
iterationStmt: WHILE '(' expression ')' statement
    | FOR '(' expressionStmt expressionStmt expression? ')' statement
    ;

returnStmt: RETURN expression? ';'
    ;

// Asignacion solo a una variable (o elemento de array): asociativa a derecha.
expression: var '=' expression
    | logicalOrExpression
    ;

var: ID ('[' expression ']')?
    ;

logicalOrExpression: logicalAndExpression ('||' logicalAndExpression)*
    ;

logicalAndExpression: simpleExpression ('&&' simpleExpression)*
    ;

// Un solo operador relacional sin parentesis, igual que en Louden.
simpleExpression: additiveExpression (relop additiveExpression)?
    ;

relop: '<='
    | '<'
    | '>'
    | '>='
    | '=='
    | '!='
    ;

additiveExpression: term (('+' | '-') term)*
    ;

term: unaryExpression (('*' | '/' | '%') unaryExpression)*
    ;

// El ! y el - unarios tienen mayor precedencia que * / %, + -, relaciones, && y ||.
unaryExpression: ('!' | '-') unaryExpression
    | factor
    ;

factor: '(' expression ')'
    | var
    | call
    | NUM
    | CHAR_LITERAL
    | TRUE
    | FALSE
    ;

call: ID '(' args ')'
    ;

args: (expression (',' expression)*)?
    ;

// ANALISIS LEXICO

IF      : 'if';
ELSE    : 'else';
INT     : 'int';
RETURN  : 'return';
VOID    : 'void';
WHILE   : 'while';
FOR     : 'for';
CHAR    : 'char';
BOOL    : 'bool';
TRUE    : 'true';
FALSE   : 'false';

// Solo letras, tal como exige Louden; las cifras no forman parte de ID.
ID      : [a-zA-Z]+;
NUM     : [0-9]+;

// Un caracter o uno de los escapes habituales de C.
CHAR_LITERAL : '\'' ( ~['\\\r\n] | '\\' [ntr0'\\] ) '\'';

// Se identifican explicitamente casos lexicos invalidos.
INVALID_ID : [a-zA-Z]+ [0-9] [a-zA-Z0-9]*;
COMMENT : '/*' .*? '*/' -> skip;
WS      : [ \t\r\n]+ -> skip;
INVALID_CHAR : .;
