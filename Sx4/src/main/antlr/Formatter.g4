grammar Formatter;

parse: expr+;

expr: '(' expr '?' parse (':' parse)? ')'               #Ternary
    | '(' parse ' if ' expr (' else ' parse)? ')'       #TernaryPy
    | '{' expr '}'                                      #Format
    | '{' '}'                                           #Empty
    | '(' condition+ ')'                                #Cond
    | expr EQUAL expr                                   #Equal
    | expr NOT_EQUAL expr                               #NotEqual
    | expr MORE_THAN expr                               #MoreThan
    | expr MORE_THAN_EQUAL expr                         #MoreThanEqual
    | expr LESS_THAN expr                               #LessThan
    | expr LESS_THAN_EQUAL expr                         #LessThanEqual
    | 'upper(' parse ')'                                #Upper
    | 'lower(' parse ')'                                #Lower
    | 'title(' parse ')'                                #Title
    | ID                                                #ID
    | STRING                                            #String
    ;

condition: expr? op=(AND|OR) expr;

EQUAL: '==';
NOT_EQUAL: '!=';
MORE_THAN: '>';
MORE_THAN_EQUAL: '>=';
LESS_THAN: '<';
LESS_THAN_EQUAL: '<=';
AND: '&&';
OR: '||';

STRING: (ID|.+?);
ID: [a-zA-Z\\.]+;