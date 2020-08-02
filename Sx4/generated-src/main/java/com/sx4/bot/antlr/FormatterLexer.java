// Generated from Formatter.g4 by ANTLR 4.7.2
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
public class FormatterLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.7.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, EQUAL=12, NOT_EQUAL=13, MORE_THAN=14, MORE_THAN_EQUAL=15, 
		LESS_THAN=16, LESS_THAN_EQUAL=17, AND=18, OR=19, STRING=20, ID=21;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
			"T__9", "T__10", "EQUAL", "NOT_EQUAL", "MORE_THAN", "MORE_THAN_EQUAL", 
			"LESS_THAN", "LESS_THAN_EQUAL", "AND", "OR", "STRING", "ID"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'('", "'?'", "':'", "')'", "' if '", "' else '", "'{'", "'}'", 
			"'upper('", "'lower('", "'title('", "'=='", "'!='", "'>'", "'>='", "'<'", 
			"'<='", "'&&'", "'||'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			"EQUAL", "NOT_EQUAL", "MORE_THAN", "MORE_THAN_EQUAL", "LESS_THAN", "LESS_THAN_EQUAL", 
			"AND", "OR", "STRING", "ID"
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


	public FormatterLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "Formatter.g4"; }

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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\27}\b\1\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\3\2\3\2\3\3\3\3\3\4\3\4\3\5\3"+
		"\5\3\6\3\6\3\6\3\6\3\6\3\7\3\7\3\7\3\7\3\7\3\7\3\7\3\b\3\b\3\t\3\t\3\n"+
		"\3\n\3\n\3\n\3\n\3\n\3\n\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\f\3\f\3"+
		"\f\3\f\3\f\3\f\3\f\3\r\3\r\3\r\3\16\3\16\3\16\3\17\3\17\3\20\3\20\3\20"+
		"\3\21\3\21\3\22\3\22\3\22\3\23\3\23\3\23\3\24\3\24\3\24\3\25\3\25\6\25"+
		"s\n\25\r\25\16\25t\5\25w\n\25\3\26\6\26z\n\26\r\26\16\26{\3t\2\27\3\3"+
		"\5\4\7\5\t\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33\17\35\20\37\21"+
		"!\22#\23%\24\'\25)\26+\27\3\2\3\6\2\60\60C\\^^c|\2\177\2\3\3\2\2\2\2\5"+
		"\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2"+
		"\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33"+
		"\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2\2\2\2%\3\2\2\2\2"+
		"\'\3\2\2\2\2)\3\2\2\2\2+\3\2\2\2\3-\3\2\2\2\5/\3\2\2\2\7\61\3\2\2\2\t"+
		"\63\3\2\2\2\13\65\3\2\2\2\r:\3\2\2\2\17A\3\2\2\2\21C\3\2\2\2\23E\3\2\2"+
		"\2\25L\3\2\2\2\27S\3\2\2\2\31Z\3\2\2\2\33]\3\2\2\2\35`\3\2\2\2\37b\3\2"+
		"\2\2!e\3\2\2\2#g\3\2\2\2%j\3\2\2\2\'m\3\2\2\2)v\3\2\2\2+y\3\2\2\2-.\7"+
		"*\2\2.\4\3\2\2\2/\60\7A\2\2\60\6\3\2\2\2\61\62\7<\2\2\62\b\3\2\2\2\63"+
		"\64\7+\2\2\64\n\3\2\2\2\65\66\7\"\2\2\66\67\7k\2\2\678\7h\2\289\7\"\2"+
		"\29\f\3\2\2\2:;\7\"\2\2;<\7g\2\2<=\7n\2\2=>\7u\2\2>?\7g\2\2?@\7\"\2\2"+
		"@\16\3\2\2\2AB\7}\2\2B\20\3\2\2\2CD\7\177\2\2D\22\3\2\2\2EF\7w\2\2FG\7"+
		"r\2\2GH\7r\2\2HI\7g\2\2IJ\7t\2\2JK\7*\2\2K\24\3\2\2\2LM\7n\2\2MN\7q\2"+
		"\2NO\7y\2\2OP\7g\2\2PQ\7t\2\2QR\7*\2\2R\26\3\2\2\2ST\7v\2\2TU\7k\2\2U"+
		"V\7v\2\2VW\7n\2\2WX\7g\2\2XY\7*\2\2Y\30\3\2\2\2Z[\7?\2\2[\\\7?\2\2\\\32"+
		"\3\2\2\2]^\7#\2\2^_\7?\2\2_\34\3\2\2\2`a\7@\2\2a\36\3\2\2\2bc\7@\2\2c"+
		"d\7?\2\2d \3\2\2\2ef\7>\2\2f\"\3\2\2\2gh\7>\2\2hi\7?\2\2i$\3\2\2\2jk\7"+
		"(\2\2kl\7(\2\2l&\3\2\2\2mn\7~\2\2no\7~\2\2o(\3\2\2\2pw\5+\26\2qs\13\2"+
		"\2\2rq\3\2\2\2st\3\2\2\2tu\3\2\2\2tr\3\2\2\2uw\3\2\2\2vp\3\2\2\2vr\3\2"+
		"\2\2w*\3\2\2\2xz\t\2\2\2yx\3\2\2\2z{\3\2\2\2{y\3\2\2\2{|\3\2\2\2|,\3\2"+
		"\2\2\6\2tv{\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}