// Generated from Calc.g4 by ANTLR 4.7.2
package com.sx4.bot.antlr;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

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
			null, "'='", "'('", "')'", "'round('", "'ceil('", "'floor('", "'abs('", 
			"'min('", "','", "'max('", "'sigma('", "'/'", null, "'**'", "'+'", "'-'", 
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\35\u00a6\b\1\4\2"+
		"\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4"+
		"\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22"+
		"\t\22\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31"+
		"\t\31\4\32\t\32\4\33\t\33\4\34\t\34\3\2\3\2\3\3\3\3\3\4\3\4\3\5\3\5\3"+
		"\5\3\5\3\5\3\5\3\5\3\6\3\6\3\6\3\6\3\6\3\6\3\7\3\7\3\7\3\7\3\7\3\7\3\7"+
		"\3\b\3\b\3\b\3\b\3\b\3\t\3\t\3\t\3\t\3\t\3\n\3\n\3\13\3\13\3\13\3\13\3"+
		"\13\3\f\3\f\3\f\3\f\3\f\3\f\3\f\3\r\3\r\3\16\3\16\3\17\3\17\3\17\3\20"+
		"\3\20\3\21\3\21\3\22\3\22\3\23\3\23\3\24\3\24\3\25\3\25\3\26\3\26\3\26"+
		"\3\27\3\27\3\27\3\30\3\30\3\30\3\31\6\31\u0089\n\31\r\31\16\31\u008a\3"+
		"\31\5\31\u008e\n\31\3\31\7\31\u0091\n\31\f\31\16\31\u0094\13\31\3\32\6"+
		"\32\u0097\n\32\r\32\16\32\u0098\3\33\5\33\u009c\n\33\3\33\3\33\3\34\6"+
		"\34\u00a1\n\34\r\34\16\34\u00a2\3\34\3\34\2\2\35\3\3\5\4\7\5\t\6\13\7"+
		"\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33\17\35\20\37\21!\22#\23%\24\'\25"+
		")\26+\27-\30/\31\61\32\63\33\65\34\67\35\3\2\b\4\2,,zz\3\2\62;\4\2\60"+
		"\60^^\5\2C\\aac|\4\2\f\f==\4\2\13\13\"\"\2\u00ab\2\3\3\2\2\2\2\5\3\2\2"+
		"\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21"+
		"\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2"+
		"\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2\2\2\2%\3\2\2\2\2\'\3"+
		"\2\2\2\2)\3\2\2\2\2+\3\2\2\2\2-\3\2\2\2\2/\3\2\2\2\2\61\3\2\2\2\2\63\3"+
		"\2\2\2\2\65\3\2\2\2\2\67\3\2\2\2\39\3\2\2\2\5;\3\2\2\2\7=\3\2\2\2\t?\3"+
		"\2\2\2\13F\3\2\2\2\rL\3\2\2\2\17S\3\2\2\2\21X\3\2\2\2\23]\3\2\2\2\25_"+
		"\3\2\2\2\27d\3\2\2\2\31k\3\2\2\2\33m\3\2\2\2\35o\3\2\2\2\37r\3\2\2\2!"+
		"t\3\2\2\2#v\3\2\2\2%x\3\2\2\2\'z\3\2\2\2)|\3\2\2\2+~\3\2\2\2-\u0081\3"+
		"\2\2\2/\u0084\3\2\2\2\61\u0088\3\2\2\2\63\u0096\3\2\2\2\65\u009b\3\2\2"+
		"\2\67\u00a0\3\2\2\29:\7?\2\2:\4\3\2\2\2;<\7*\2\2<\6\3\2\2\2=>\7+\2\2>"+
		"\b\3\2\2\2?@\7t\2\2@A\7q\2\2AB\7w\2\2BC\7p\2\2CD\7f\2\2DE\7*\2\2E\n\3"+
		"\2\2\2FG\7e\2\2GH\7g\2\2HI\7k\2\2IJ\7n\2\2JK\7*\2\2K\f\3\2\2\2LM\7h\2"+
		"\2MN\7n\2\2NO\7q\2\2OP\7q\2\2PQ\7t\2\2QR\7*\2\2R\16\3\2\2\2ST\7c\2\2T"+
		"U\7d\2\2UV\7u\2\2VW\7*\2\2W\20\3\2\2\2XY\7o\2\2YZ\7k\2\2Z[\7p\2\2[\\\7"+
		"*\2\2\\\22\3\2\2\2]^\7.\2\2^\24\3\2\2\2_`\7o\2\2`a\7c\2\2ab\7z\2\2bc\7"+
		"*\2\2c\26\3\2\2\2de\7u\2\2ef\7k\2\2fg\7i\2\2gh\7o\2\2hi\7c\2\2ij\7*\2"+
		"\2j\30\3\2\2\2kl\7\61\2\2l\32\3\2\2\2mn\t\2\2\2n\34\3\2\2\2op\7,\2\2p"+
		"q\7,\2\2q\36\3\2\2\2rs\7-\2\2s \3\2\2\2tu\7/\2\2u\"\3\2\2\2vw\7~\2\2w"+
		"$\3\2\2\2xy\7(\2\2y&\3\2\2\2z{\7`\2\2{(\3\2\2\2|}\7\u0080\2\2}*\3\2\2"+
		"\2~\177\7>\2\2\177\u0080\7>\2\2\u0080,\3\2\2\2\u0081\u0082\7@\2\2\u0082"+
		"\u0083\7@\2\2\u0083.\3\2\2\2\u0084\u0085\7r\2\2\u0085\u0086\7k\2\2\u0086"+
		"\60\3\2\2\2\u0087\u0089\t\3\2\2\u0088\u0087\3\2\2\2\u0089\u008a\3\2\2"+
		"\2\u008a\u0088\3\2\2\2\u008a\u008b\3\2\2\2\u008b\u008d\3\2\2\2\u008c\u008e"+
		"\t\4\2\2\u008d\u008c\3\2\2\2\u008d\u008e\3\2\2\2\u008e\u0092\3\2\2\2\u008f"+
		"\u0091\t\3\2\2\u0090\u008f\3\2\2\2\u0091\u0094\3\2\2\2\u0092\u0090\3\2"+
		"\2\2\u0092\u0093\3\2\2\2\u0093\62\3\2\2\2\u0094\u0092\3\2\2\2\u0095\u0097"+
		"\t\5\2\2\u0096\u0095\3\2\2\2\u0097\u0098\3\2\2\2\u0098\u0096\3\2\2\2\u0098"+
		"\u0099\3\2\2\2\u0099\64\3\2\2\2\u009a\u009c\7\17\2\2\u009b\u009a\3\2\2"+
		"\2\u009b\u009c\3\2\2\2\u009c\u009d\3\2\2\2\u009d\u009e\t\6\2\2\u009e\66"+
		"\3\2\2\2\u009f\u00a1\t\7\2\2\u00a0\u009f\3\2\2\2\u00a1\u00a2\3\2\2\2\u00a2"+
		"\u00a0\3\2\2\2\u00a2\u00a3\3\2\2\2\u00a3\u00a4\3\2\2\2\u00a4\u00a5\b\34"+
		"\2\2\u00a58\3\2\2\2\t\2\u008a\u008d\u0092\u0098\u009b\u00a2\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}