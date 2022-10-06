// Generated from java-escape by ANTLR 4.11.1
package com.sx4.bot.antlr;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class CalcLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.11.1", RuntimeMetaData.VERSION); }

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
		"\u0004\u0000\u001b\u00a3\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002"+
		"\u0001\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002"+
		"\u0004\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002"+
		"\u0007\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002"+
		"\u000b\u0007\u000b\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e"+
		"\u0002\u000f\u0007\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011"+
		"\u0002\u0012\u0007\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014"+
		"\u0002\u0015\u0007\u0015\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017"+
		"\u0002\u0018\u0007\u0018\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a"+
		"\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0002\u0001\u0002"+
		"\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003"+
		"\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004"+
		"\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007"+
		"\u0001\u0007\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\t\u0001\t\u0001"+
		"\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\u000b\u0001\u000b\u0001\f\u0001"+
		"\f\u0001\r\u0001\r\u0001\r\u0001\u000e\u0001\u000e\u0001\u000f\u0001\u000f"+
		"\u0001\u0010\u0001\u0010\u0001\u0011\u0001\u0011\u0001\u0012\u0001\u0012"+
		"\u0001\u0013\u0001\u0013\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0015"+
		"\u0001\u0015\u0001\u0015\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0017"+
		"\u0004\u0017\u0086\b\u0017\u000b\u0017\f\u0017\u0087\u0001\u0017\u0003"+
		"\u0017\u008b\b\u0017\u0001\u0017\u0005\u0017\u008e\b\u0017\n\u0017\f\u0017"+
		"\u0091\t\u0017\u0001\u0018\u0004\u0018\u0094\b\u0018\u000b\u0018\f\u0018"+
		"\u0095\u0001\u0019\u0003\u0019\u0099\b\u0019\u0001\u0019\u0001\u0019\u0001"+
		"\u001a\u0004\u001a\u009e\b\u001a\u000b\u001a\f\u001a\u009f\u0001\u001a"+
		"\u0001\u001a\u0000\u0000\u001b\u0001\u0001\u0003\u0002\u0005\u0003\u0007"+
		"\u0004\t\u0005\u000b\u0006\r\u0007\u000f\b\u0011\t\u0013\n\u0015\u000b"+
		"\u0017\f\u0019\r\u001b\u000e\u001d\u000f\u001f\u0010!\u0011#\u0012%\u0013"+
		"\'\u0014)\u0015+\u0016-\u0017/\u00181\u00193\u001a5\u001b\u0001\u0000"+
		"\u0006\u0002\u0000**xx\u0001\u000009\u0002\u0000..\\\\\u0003\u0000AZ_"+
		"_az\u0002\u0000\n\n;;\u0002\u0000\t\t  \u00a8\u0000\u0001\u0001\u0000"+
		"\u0000\u0000\u0000\u0003\u0001\u0000\u0000\u0000\u0000\u0005\u0001\u0000"+
		"\u0000\u0000\u0000\u0007\u0001\u0000\u0000\u0000\u0000\t\u0001\u0000\u0000"+
		"\u0000\u0000\u000b\u0001\u0000\u0000\u0000\u0000\r\u0001\u0000\u0000\u0000"+
		"\u0000\u000f\u0001\u0000\u0000\u0000\u0000\u0011\u0001\u0000\u0000\u0000"+
		"\u0000\u0013\u0001\u0000\u0000\u0000\u0000\u0015\u0001\u0000\u0000\u0000"+
		"\u0000\u0017\u0001\u0000\u0000\u0000\u0000\u0019\u0001\u0000\u0000\u0000"+
		"\u0000\u001b\u0001\u0000\u0000\u0000\u0000\u001d\u0001\u0000\u0000\u0000"+
		"\u0000\u001f\u0001\u0000\u0000\u0000\u0000!\u0001\u0000\u0000\u0000\u0000"+
		"#\u0001\u0000\u0000\u0000\u0000%\u0001\u0000\u0000\u0000\u0000\'\u0001"+
		"\u0000\u0000\u0000\u0000)\u0001\u0000\u0000\u0000\u0000+\u0001\u0000\u0000"+
		"\u0000\u0000-\u0001\u0000\u0000\u0000\u0000/\u0001\u0000\u0000\u0000\u0000"+
		"1\u0001\u0000\u0000\u0000\u00003\u0001\u0000\u0000\u0000\u00005\u0001"+
		"\u0000\u0000\u0000\u00017\u0001\u0000\u0000\u0000\u00039\u0001\u0000\u0000"+
		"\u0000\u0005;\u0001\u0000\u0000\u0000\u0007=\u0001\u0000\u0000\u0000\t"+
		"C\u0001\u0000\u0000\u0000\u000bJ\u0001\u0000\u0000\u0000\rP\u0001\u0000"+
		"\u0000\u0000\u000fW\u0001\u0000\u0000\u0000\u0011\\\u0001\u0000\u0000"+
		"\u0000\u0013a\u0001\u0000\u0000\u0000\u0015c\u0001\u0000\u0000\u0000\u0017"+
		"h\u0001\u0000\u0000\u0000\u0019j\u0001\u0000\u0000\u0000\u001bl\u0001"+
		"\u0000\u0000\u0000\u001do\u0001\u0000\u0000\u0000\u001fq\u0001\u0000\u0000"+
		"\u0000!s\u0001\u0000\u0000\u0000#u\u0001\u0000\u0000\u0000%w\u0001\u0000"+
		"\u0000\u0000\'y\u0001\u0000\u0000\u0000){\u0001\u0000\u0000\u0000+~\u0001"+
		"\u0000\u0000\u0000-\u0081\u0001\u0000\u0000\u0000/\u0085\u0001\u0000\u0000"+
		"\u00001\u0093\u0001\u0000\u0000\u00003\u0098\u0001\u0000\u0000\u00005"+
		"\u009d\u0001\u0000\u0000\u000078\u0005=\u0000\u00008\u0002\u0001\u0000"+
		"\u0000\u00009:\u0005(\u0000\u0000:\u0004\u0001\u0000\u0000\u0000;<\u0005"+
		")\u0000\u0000<\u0006\u0001\u0000\u0000\u0000=>\u0005s\u0000\u0000>?\u0005"+
		"q\u0000\u0000?@\u0005r\u0000\u0000@A\u0005t\u0000\u0000AB\u0005(\u0000"+
		"\u0000B\b\u0001\u0000\u0000\u0000CD\u0005r\u0000\u0000DE\u0005o\u0000"+
		"\u0000EF\u0005u\u0000\u0000FG\u0005n\u0000\u0000GH\u0005d\u0000\u0000"+
		"HI\u0005(\u0000\u0000I\n\u0001\u0000\u0000\u0000JK\u0005c\u0000\u0000"+
		"KL\u0005e\u0000\u0000LM\u0005i\u0000\u0000MN\u0005l\u0000\u0000NO\u0005"+
		"(\u0000\u0000O\f\u0001\u0000\u0000\u0000PQ\u0005f\u0000\u0000QR\u0005"+
		"l\u0000\u0000RS\u0005o\u0000\u0000ST\u0005o\u0000\u0000TU\u0005r\u0000"+
		"\u0000UV\u0005(\u0000\u0000V\u000e\u0001\u0000\u0000\u0000WX\u0005a\u0000"+
		"\u0000XY\u0005b\u0000\u0000YZ\u0005s\u0000\u0000Z[\u0005(\u0000\u0000"+
		"[\u0010\u0001\u0000\u0000\u0000\\]\u0005m\u0000\u0000]^\u0005i\u0000\u0000"+
		"^_\u0005n\u0000\u0000_`\u0005(\u0000\u0000`\u0012\u0001\u0000\u0000\u0000"+
		"ab\u0005,\u0000\u0000b\u0014\u0001\u0000\u0000\u0000cd\u0005m\u0000\u0000"+
		"de\u0005a\u0000\u0000ef\u0005x\u0000\u0000fg\u0005(\u0000\u0000g\u0016"+
		"\u0001\u0000\u0000\u0000hi\u0005/\u0000\u0000i\u0018\u0001\u0000\u0000"+
		"\u0000jk\u0007\u0000\u0000\u0000k\u001a\u0001\u0000\u0000\u0000lm\u0005"+
		"*\u0000\u0000mn\u0005*\u0000\u0000n\u001c\u0001\u0000\u0000\u0000op\u0005"+
		"+\u0000\u0000p\u001e\u0001\u0000\u0000\u0000qr\u0005-\u0000\u0000r \u0001"+
		"\u0000\u0000\u0000st\u0005|\u0000\u0000t\"\u0001\u0000\u0000\u0000uv\u0005"+
		"&\u0000\u0000v$\u0001\u0000\u0000\u0000wx\u0005^\u0000\u0000x&\u0001\u0000"+
		"\u0000\u0000yz\u0005~\u0000\u0000z(\u0001\u0000\u0000\u0000{|\u0005<\u0000"+
		"\u0000|}\u0005<\u0000\u0000}*\u0001\u0000\u0000\u0000~\u007f\u0005>\u0000"+
		"\u0000\u007f\u0080\u0005>\u0000\u0000\u0080,\u0001\u0000\u0000\u0000\u0081"+
		"\u0082\u0005p\u0000\u0000\u0082\u0083\u0005i\u0000\u0000\u0083.\u0001"+
		"\u0000\u0000\u0000\u0084\u0086\u0007\u0001\u0000\u0000\u0085\u0084\u0001"+
		"\u0000\u0000\u0000\u0086\u0087\u0001\u0000\u0000\u0000\u0087\u0085\u0001"+
		"\u0000\u0000\u0000\u0087\u0088\u0001\u0000\u0000\u0000\u0088\u008a\u0001"+
		"\u0000\u0000\u0000\u0089\u008b\u0007\u0002\u0000\u0000\u008a\u0089\u0001"+
		"\u0000\u0000\u0000\u008a\u008b\u0001\u0000\u0000\u0000\u008b\u008f\u0001"+
		"\u0000\u0000\u0000\u008c\u008e\u0007\u0001\u0000\u0000\u008d\u008c\u0001"+
		"\u0000\u0000\u0000\u008e\u0091\u0001\u0000\u0000\u0000\u008f\u008d\u0001"+
		"\u0000\u0000\u0000\u008f\u0090\u0001\u0000\u0000\u0000\u00900\u0001\u0000"+
		"\u0000\u0000\u0091\u008f\u0001\u0000\u0000\u0000\u0092\u0094\u0007\u0003"+
		"\u0000\u0000\u0093\u0092\u0001\u0000\u0000\u0000\u0094\u0095\u0001\u0000"+
		"\u0000\u0000\u0095\u0093\u0001\u0000\u0000\u0000\u0095\u0096\u0001\u0000"+
		"\u0000\u0000\u00962\u0001\u0000\u0000\u0000\u0097\u0099\u0005\r\u0000"+
		"\u0000\u0098\u0097\u0001\u0000\u0000\u0000\u0098\u0099\u0001\u0000\u0000"+
		"\u0000\u0099\u009a\u0001\u0000\u0000\u0000\u009a\u009b\u0007\u0004\u0000"+
		"\u0000\u009b4\u0001\u0000\u0000\u0000\u009c\u009e\u0007\u0005\u0000\u0000"+
		"\u009d\u009c\u0001\u0000\u0000\u0000\u009e\u009f\u0001\u0000\u0000\u0000"+
		"\u009f\u009d\u0001\u0000\u0000\u0000\u009f\u00a0\u0001\u0000\u0000\u0000"+
		"\u00a0\u00a1\u0001\u0000\u0000\u0000\u00a1\u00a2\u0006\u001a\u0000\u0000"+
		"\u00a26\u0001\u0000\u0000\u0000\u0007\u0000\u0087\u008a\u008f\u0095\u0098"+
		"\u009f\u0001\u0006\u0000\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}