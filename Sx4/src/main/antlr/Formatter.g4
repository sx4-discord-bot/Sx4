grammar Formatter;

parse: expr+;

expr: '{' expr '?' parse (':' parse)? '}'   #Ternary
    | '{' expr '}'                          #Format
    | expr EQUAL expr                       #Equal
    | expr NOT_EQUAL expr                   #NotEqual
    | expr MORE_THAN expr                   #MoreThan
    | expr MORE_THAN_EQUAL expr             #MoreThanEqual
    | expr LESS_THAN expr                   #LessThan
    | expr LESS_THAN_EQUAL expr             #LessThanEqual
    | ID                                    #ID
    | STRING                                #String
    ;

EQUAL: '==';
NOT_EQUAL: '!=';
MORE_THAN: '>';
MORE_THAN_EQUAL: '>=';
LESS_THAN: '<';
LESS_THAN_EQUAL: '<=';

STRING: (ID|.+?);
ID: [a-zA-Z\\.]+;