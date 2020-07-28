grammar Calc;

parse: stat+;

stat: expr                         #Return
    | VAR '=' expr NEWLINE         #Assign
    | NEWLINE                      #Blank
    ;

expr: expr POW expr             #Pow
    | expr DIV expr             #Div
    | expr MUL expr             #Mul
    | expr ADD expr             #Add
    | expr SUB expr             #Sub
    | expr OR expr              #Or
    | expr AND expr             #And
    | expr XOR expr             #Xor
    | NOT expr                  #Not
    | expr SHIFT_LEFT expr      #ShiftLeft
    | expr SHIFT_RIGHT expr     #ShiftRight
    | DOUBLE                    #Double
    | VAR                       #Var
    | PI                        #Pi
    | '(' expr ')'              #Parens
    | 'round(' expr ')'         #Round
    | 'ceil(' expr ')'          #Ceil
    | 'floor(' expr ')'         #Floor
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
VAR: [a-zA-Z]+;
NEWLINE: '\r'? ('\n'|';');
WS: [ \t]+ -> skip;