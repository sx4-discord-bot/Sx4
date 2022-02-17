// Generated from Calc.g4 by ANTLR 4.7.2
package com.sx4.bot.antlr;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class CalcLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.7.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, DIV=12, MUL=13, POW=14, ADD=15, SUB=16, OR=17, AND=18, 
		XOR=19, NOT=20, SHIFT_LEFT=21, SHIFT_RIGHT=22, PI=23, DOUBLE=24, VAR=25, 
		NEWLINE=26, WS=27;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
			"T__9", "T__10", "DIV", "MUL", "POW", "ADD", "SUB", "OR", "AND", "XOR", 
			"NOT", "SHIFT_LEFT", "SHIFT_RIGHT", "PI", "DOUBLE", "VAR", "NEWLINE", 
			"WS"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'='", "'('", "')'", "'sqrt('", "'round('", "'ceil('", "'floor('", 
			"'abs('", "'min('", "','", "'max('", "'/'", null, "'**'", "'+'", "'-'", 
			"'|'", "'&'", "'^'", "'~'", "'<<'", "'>>'", "'pi'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			"DIV", "MUL", "POW", "ADD", "SUB", "OR", "AND", "XOR", "NOT", "SHIFT_LEFT", 
			"SHIFT_RIGHT", "PI", "DOUBLE", "VAR", "NEWLINE", "WS"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}


	public CalcLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "Calc.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\35\u00a5\b\1\4\2"+
		"\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4"+
		"\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22"+
		"\t\22\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31"+
		"\t\31\4\32\t\32\4\33\t\33\4\34\t\34\3\2\3\2\3\3\3\3\3\4\3\4\3\5\3\5\3"+
		"\5\3\5\3\5\3\5\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\7\3\7\3\7\3\7\3\7\3\7\3\b"+
		"\3\b\3\b\3\b\3\b\3\b\3\b\3\t\3\t\3\t\3\t\3\t\3\n\3\n\3\n\3\n\3\n\3\13"+
		"\3\13\3\f\3\f\3\f\3\f\3\f\3\r\3\r\3\16\3\16\3\17\3\17\3\17\3\20\3\20\3"+
		"\21\3\21\3\22\3\22\3\23\3\23\3\24\3\24\3\25\3\25\3\26\3\26\3\26\3\27\3"+
		"\27\3\27\3\30\3\30\3\30\3\31\6\31\u0088\n\31\r\31\16\31\u0089\3\31\5\31"+
		"\u008d\n\31\3\31\7\31\u0090\n\31\f\31\16\31\u0093\13\31\3\32\6\32\u0096"+
		"\n\32\r\32\16\32\u0097\3\33\5\33\u009b\n\33\3\33\3\33\3\34\6\34\u00a0"+
		"\n\34\r\34\16\34\u00a1\3\34\3\34\2\2\35\3\3\5\4\7\5\t\6\13\7\r\b\17\t"+
		"\21\n\23\13\25\f\27\r\31\16\33\17\35\20\37\21!\22#\23%\24\'\25)\26+\27"+
		"-\30/\31\61\32\63\33\65\34\67\35\3\2\b\4\2,,zz\3\2\62;\4\2\60\60^^\5\2"+
		"C\\aac|\4\2\f\f==\4\2\13\13\"\"\2\u00aa\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3"+
		"\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2\2"+
		"\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2\2\2\2\35"+
		"\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2\2\2\2%\3\2\2\2\2\'\3\2\2\2\2)"+
		"\3\2\2\2\2+\3\2\2\2\2-\3\2\2\2\2/\3\2\2\2\2\61\3\2\2\2\2\63\3\2\2\2\2"+
		"\65\3\2\2\2\2\67\3\2\2\2\39\3\2\2\2\5;\3\2\2\2\7=\3\2\2\2\t?\3\2\2\2\13"+
		"E\3\2\2\2\rL\3\2\2\2\17R\3\2\2\2\21Y\3\2\2\2\23^\3\2\2\2\25c\3\2\2\2\27"+
		"e\3\2\2\2\31j\3\2\2\2\33l\3\2\2\2\35n\3\2\2\2\37q\3\2\2\2!s\3\2\2\2#u"+
		"\3\2\2\2%w\3\2\2\2\'y\3\2\2\2){\3\2\2\2+}\3\2\2\2-\u0080\3\2\2\2/\u0083"+
		"\3\2\2\2\61\u0087\3\2\2\2\63\u0095\3\2\2\2\65\u009a\3\2\2\2\67\u009f\3"+
		"\2\2\29:\7?\2\2:\4\3\2\2\2;<\7*\2\2<\6\3\2\2\2=>\7+\2\2>\b\3\2\2\2?@\7"+
		"u\2\2@A\7s\2\2AB\7t\2\2BC\7v\2\2CD\7*\2\2D\n\3\2\2\2EF\7t\2\2FG\7q\2\2"+
		"GH\7w\2\2HI\7p\2\2IJ\7f\2\2JK\7*\2\2K\f\3\2\2\2LM\7e\2\2MN\7g\2\2NO\7"+
		"k\2\2OP\7n\2\2PQ\7*\2\2Q\16\3\2\2\2RS\7h\2\2ST\7n\2\2TU\7q\2\2UV\7q\2"+
		"\2VW\7t\2\2WX\7*\2\2X\20\3\2\2\2YZ\7c\2\2Z[\7d\2\2[\\\7u\2\2\\]\7*\2\2"+
		"]\22\3\2\2\2^_\7o\2\2_`\7k\2\2`a\7p\2\2ab\7*\2\2b\24\3\2\2\2cd\7.\2\2"+
		"d\26\3\2\2\2ef\7o\2\2fg\7c\2\2gh\7z\2\2hi\7*\2\2i\30\3\2\2\2jk\7\61\2"+
		"\2k\32\3\2\2\2lm\t\2\2\2m\34\3\2\2\2no\7,\2\2op\7,\2\2p\36\3\2\2\2qr\7"+
		"-\2\2r \3\2\2\2st\7/\2\2t\"\3\2\2\2uv\7~\2\2v$\3\2\2\2wx\7(\2\2x&\3\2"+
		"\2\2yz\7`\2\2z(\3\2\2\2{|\7\u0080\2\2|*\3\2\2\2}~\7>\2\2~\177\7>\2\2\177"+
		",\3\2\2\2\u0080\u0081\7@\2\2\u0081\u0082\7@\2\2\u0082.\3\2\2\2\u0083\u0084"+
		"\7r\2\2\u0084\u0085\7k\2\2\u0085\60\3\2\2\2\u0086\u0088\t\3\2\2\u0087"+
		"\u0086\3\2\2\2\u0088\u0089\3\2\2\2\u0089\u0087\3\2\2\2\u0089\u008a\3\2"+
		"\2\2\u008a\u008c\3\2\2\2\u008b\u008d\t\4\2\2\u008c\u008b\3\2\2\2\u008c"+
		"\u008d\3\2\2\2\u008d\u0091\3\2\2\2\u008e\u0090\t\3\2\2\u008f\u008e\3\2"+
		"\2\2\u0090\u0093\3\2\2\2\u0091\u008f\3\2\2\2\u0091\u0092\3\2\2\2\u0092"+
		"\62\3\2\2\2\u0093\u0091\3\2\2\2\u0094\u0096\t\5\2\2\u0095\u0094\3\2\2"+
		"\2\u0096\u0097\3\2\2\2\u0097\u0095\3\2\2\2\u0097\u0098\3\2\2\2\u0098\64"+
		"\3\2\2\2\u0099\u009b\7\17\2\2\u009a\u0099\3\2\2\2\u009a\u009b\3\2\2\2"+
		"\u009b\u009c\3\2\2\2\u009c\u009d\t\6\2\2\u009d\66\3\2\2\2\u009e\u00a0"+
		"\t\7\2\2\u009f\u009e\3\2\2\2\u00a0\u00a1\3\2\2\2\u00a1\u009f\3\2\2\2\u00a1"+
		"\u00a2\3\2\2\2\u00a2\u00a3\3\2\2\2\u00a3\u00a4\b\34\2\2\u00a48\3\2\2\2"+
		"\t\2\u0089\u008c\u0091\u0097\u009a\u00a1\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}