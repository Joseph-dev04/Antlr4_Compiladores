grammar CMinus ;

program : declarationList EOF;
declarationList : declaration+;

declaration
    : varDeclaration
    | funDeclaration
    ;

varDeclaration
    : typeSpecifier ID SEMI
    | typeSpecifier ID LBRACK NUM RBRACK SEMI
    ;

typeSpecifier
    : INT
    | VOID
    | CHAR
    | BOOL
    ;

funDeclaration
    : typeSpecifier ID LPAREN params RPAREN compoundStmt
    ;

params
    : paramList
    | VOID
    ;

paramList : param (COMMA param)*;

param
    : typeSpecifier ID
    | typeSpecifier ID LBRACK RBRACK
    ;

compoundStmt : LBRACE localDeclarations statementList RBRACE;

localDeclarations : varDeclaration* ;

statementList : statement* ;

statement
    : expressionStmt
    | compoundStmt
    | selectionStmt
    | iterationStmt
    | returnStmt
    ;

expressionStmt
    : expression SEMI
    | SEMI
    ;

selectionStmt
    : IF LPAREN expression RPAREN statement (ELSE statement)?
    ;

iterationStmt
    : WHILE LPAREN expression RPAREN statement
    | FOR LPAREN expressionStmt expressionStmt expression? RPAREN statement
    ;

returnStmt
    : RETURN SEMI
    | RETURN expression SEMI
    ;

expression
    : var ASSIGN expression
    | logicalOrExpression
    ;

logicalOrExpression
    : logicalOrExpression OR logicalAndExpression
    | logicalAndExpression
    ;

logicalAndExpression
    : logicalAndExpression AND equalityExpression
    | equalityExpression
    ;

equalityExpression
    : simpleExpression (eqop simpleExpression)?
    ;

eqop : EQ | NEQ ;

simpleExpression 
    : additiveExpression (relop additiveExpression)? 
    ;

var
    : ID
    | ID LBRACK expression RBRACK
    ;


relop : LTE | LT | GT | GTE | EQ | NEQ ;

additiveExpression
    : additiveExpression addop term
    | term
    ;

addop : PLUS | MINUS ;

term
    : term mulop factor
    | factor
    ;

mulop : TIMES | OVER | MOD ;

factor
    : LPAREN expression RPAREN
    | var
    | call
    | NUM
    | CHAR_LITERAL
    | TRUE
    | FALSE
    | MINUS factor
    | NOT factor
    ;

call    : ID LPAREN args RPAREN ;

args    : argList? ;

argList : expression (COMMA expression)* ;

IF      : 'if' ;
ELSE    : 'else' ;
INT     : 'int' ;
RETURN  : 'return' ;
VOID    : 'void' ;
WHILE   : 'while' ;
FOR     : 'for' ;
CHAR    : 'char' ;
BOOL    : 'bool' ;
TRUE    : 'true' ;
FALSE   : 'false' ;

MOD     : '%' ;
AND     : '&&' ;
OR      : '||' ;
NOT     : '!' ;
PLUS    : '+' ;
MINUS   : '-' ;
TIMES   : '*' ;
OVER    : '/' ;
LT      : '<' ;
LTE     : '<=' ;
GT      : '>' ;
GTE     : '>=' ;
EQ      : '==' ;
NEQ     : '!=' ;
ASSIGN  : '=' ;
SEMI    : ';' ;
COMMA   : ',' ;
LPAREN  : '(' ;
RPAREN  : ')' ;
LBRACK  : '[' ;
RBRACK  : ']' ;
LBRACE  : '{' ;
RBRACE  : '}' ;

ID      : [a-zA-Z] [a-zA-Z0-9]* ;
NUM     : [0-9]+ ;

BLOCK_COMMENT : '/*' .*? '*/' -> skip ;
WS      : [ \t\r\n\u00A0]+ -> skip ;
CHAR_LITERAL : '\'' ( '\\' [nrt'\\0] | ~['\\\r\n] ) '\'' ;