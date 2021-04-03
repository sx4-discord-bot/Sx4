grammar Calc;

parse: stat+;

stat: expr                         #Return
    | VAR '=' expr NEWLINE         #Assign
    | NEWLINE                      #Blank
    ;

expr: expr POW expr                         #Pow
    | expr DIV expr                         #Div
    | expr MUL expr                         #Mul
    | expr ADD expr                         #Add
    | expr SUB expr                         #Sub
    | expr OR expr                          #Or
    | expr AND expr                         #And
    | expr XOR expr                         #Xor
    | NOT expr                              #Not
    | expr SHIFT_LEFT expr                  #ShiftLeft
    | expr SHIFT_RIGHT expr                 #ShiftRight
    | DOUBLE                                #Double
    | VAR                                   #Var
    | PI                                    #Pi
    | '(' expr ')'                          #Parens
    | 'sqrt(' expr ')'                      #Sqrt
    | 'round(' expr ')'                     #Round
    | 'ceil(' expr ')'                      #Ceil
    | 'floor(' expr ')'                     #Floor
    | 'abs(' expr ')'                       #Abs
    | 'min(' expr ',' expr ')'              #Min
    | 'max(' expr ',' expr ')'              #Max
    | 'sigma(' expr ',' expr ',' expr ')'   #Sigma
    ;

DIV: '/';
MUL: ('*'|'x');
POW: '**';
ADD: '+';
SUB: '-';
OR: '|';
AND: '&';
XOR: '^';
NOT: '~';
SHIFT_LEFT: '<<';
SHIFT_RIGHT: '>>';
PI: 'pi';
DOUBLE: [0-9]+[\\.]?[0-9]*;
VAR: [a-zA-Z_]+;
NEWLINE: '\r'? ('\n'|';');
WS: [ \t]+ -> skip;