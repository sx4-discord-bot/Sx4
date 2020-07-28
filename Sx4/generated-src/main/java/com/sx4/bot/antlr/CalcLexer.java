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
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, DIV=7, MUL=8, POW=9, ADD=10, 
		SUB=11, OR=12, AND=13, XOR=14, NOT=15, SHIFT_LEFT=16, SHIFT_RIGHT=17, 
		PI=18, DOUBLE=19, VAR=20, NEWLINE=21, WS=22;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "DIV", "MUL", "POW", 
			"ADD", "SUB", "OR", "AND", "XOR", "NOT", "SHIFT_LEFT", "SHIFT_RIGHT", 
			"PI", "DOUBLE", "VAR", "NEWLINE", "WS"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'='", "'('", "')'", "'round('", "'ceil('", "'floor('", "'/'", 
			null, "'**'", "'+'", "'-'", "'|'", "'&'", "'^'", "'~'", "'<<'", "'>>'", 
			"'pi'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, "DIV", "MUL", "POW", "ADD", 
			"SUB", "OR", "AND", "XOR", "NOT", "SHIFT_LEFT", "SHIFT_RIGHT", "PI", 
			"DOUBLE", "VAR", "NEWLINE", "WS"
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\30\u0084\b\1\4\2"+
		"\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4"+
		"\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22"+
		"\t\22\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\3\2\3\2\3\3\3"+
		"\3\3\4\3\4\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\6\3\6\3\6\3\6\3\6\3\6\3\7\3\7"+
		"\3\7\3\7\3\7\3\7\3\7\3\b\3\b\3\t\3\t\3\n\3\n\3\n\3\13\3\13\3\f\3\f\3\r"+
		"\3\r\3\16\3\16\3\17\3\17\3\20\3\20\3\21\3\21\3\21\3\22\3\22\3\22\3\23"+
		"\3\23\3\23\3\24\6\24g\n\24\r\24\16\24h\3\24\5\24l\n\24\3\24\7\24o\n\24"+
		"\f\24\16\24r\13\24\3\25\6\25u\n\25\r\25\16\25v\3\26\5\26z\n\26\3\26\3"+
		"\26\3\27\6\27\177\n\27\r\27\16\27\u0080\3\27\3\27\2\2\30\3\3\5\4\7\5\t"+
		"\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33\17\35\20\37\21!\22#\23"+
		"%\24\'\25)\26+\27-\30\3\2\b\4\2,,zz\3\2\62;\4\2\60\60^^\4\2C\\c|\4\2\f"+
		"\f==\4\2\13\13\"\"\2\u0089\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2"+
		"\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2\23\3\2\2\2\2"+
		"\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2\2\2\2\35\3\2\2\2\2\37\3"+
		"\2\2\2\2!\3\2\2\2\2#\3\2\2\2\2%\3\2\2\2\2\'\3\2\2\2\2)\3\2\2\2\2+\3\2"+
		"\2\2\2-\3\2\2\2\3/\3\2\2\2\5\61\3\2\2\2\7\63\3\2\2\2\t\65\3\2\2\2\13<"+
		"\3\2\2\2\rB\3\2\2\2\17I\3\2\2\2\21K\3\2\2\2\23M\3\2\2\2\25P\3\2\2\2\27"+
		"R\3\2\2\2\31T\3\2\2\2\33V\3\2\2\2\35X\3\2\2\2\37Z\3\2\2\2!\\\3\2\2\2#"+
		"_\3\2\2\2%b\3\2\2\2\'f\3\2\2\2)t\3\2\2\2+y\3\2\2\2-~\3\2\2\2/\60\7?\2"+
		"\2\60\4\3\2\2\2\61\62\7*\2\2\62\6\3\2\2\2\63\64\7+\2\2\64\b\3\2\2\2\65"+
		"\66\7t\2\2\66\67\7q\2\2\678\7w\2\289\7p\2\29:\7f\2\2:;\7*\2\2;\n\3\2\2"+
		"\2<=\7e\2\2=>\7g\2\2>?\7k\2\2?@\7n\2\2@A\7*\2\2A\f\3\2\2\2BC\7h\2\2CD"+
		"\7n\2\2DE\7q\2\2EF\7q\2\2FG\7t\2\2GH\7*\2\2H\16\3\2\2\2IJ\7\61\2\2J\20"+
		"\3\2\2\2KL\t\2\2\2L\22\3\2\2\2MN\7,\2\2NO\7,\2\2O\24\3\2\2\2PQ\7-\2\2"+
		"Q\26\3\2\2\2RS\7/\2\2S\30\3\2\2\2TU\7~\2\2U\32\3\2\2\2VW\7(\2\2W\34\3"+
		"\2\2\2XY\7`\2\2Y\36\3\2\2\2Z[\7\u0080\2\2[ \3\2\2\2\\]\7>\2\2]^\7>\2\2"+
		"^\"\3\2\2\2_`\7@\2\2`a\7@\2\2a$\3\2\2\2bc\7r\2\2cd\7k\2\2d&\3\2\2\2eg"+
		"\t\3\2\2fe\3\2\2\2gh\3\2\2\2hf\3\2\2\2hi\3\2\2\2ik\3\2\2\2jl\t\4\2\2k"+
		"j\3\2\2\2kl\3\2\2\2lp\3\2\2\2mo\t\3\2\2nm\3\2\2\2or\3\2\2\2pn\3\2\2\2"+
		"pq\3\2\2\2q(\3\2\2\2rp\3\2\2\2su\t\5\2\2ts\3\2\2\2uv\3\2\2\2vt\3\2\2\2"+
		"vw\3\2\2\2w*\3\2\2\2xz\7\17\2\2yx\3\2\2\2yz\3\2\2\2z{\3\2\2\2{|\t\6\2"+
		"\2|,\3\2\2\2}\177\t\7\2\2~}\3\2\2\2\177\u0080\3\2\2\2\u0080~\3\2\2\2\u0080"+
		"\u0081\3\2\2\2\u0081\u0082\3\2\2\2\u0082\u0083\b\27\2\2\u0083.\3\2\2\2"+
		"\t\2hkpvy\u0080\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}