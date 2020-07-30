// Generated from Formatter.g4 by ANTLR 4.7.2
package com.sx4.bot.antlr;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class FormatterParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.7.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, EQUAL=5, NOT_EQUAL=6, MORE_THAN=7, MORE_THAN_EQUAL=8, 
		LESS_THAN=9, LESS_THAN_EQUAL=10, STRING=11, ID=12;
	public static final int
		RULE_parse = 0, RULE_expr = 1;
	private static String[] makeRuleNames() {
		return new String[] {
			"parse", "expr"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'{'", "'?'", "':'", "'}'", "'=='", "'!='", "'>'", "'>='", "'<'", 
			"'<='"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, "EQUAL", "NOT_EQUAL", "MORE_THAN", "MORE_THAN_EQUAL", 
			"LESS_THAN", "LESS_THAN_EQUAL", "STRING", "ID"
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

	@Override
	public String getGrammarFileName() { return "Formatter.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public FormatterParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	public static class ParseContext extends ParserRuleContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public ParseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parse; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitParse(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParseContext parse() throws RecognitionException {
		ParseContext _localctx = new ParseContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_parse);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(5); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(4);
				expr(0);
				}
				}
				setState(7); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << STRING) | (1L << ID))) != 0) );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ExprContext extends ParserRuleContext {
		public ExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expr; }
	 
		public ExprContext() { }
		public void copyFrom(ExprContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class LessThanContext extends ExprContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode LESS_THAN() { return getToken(FormatterParser.LESS_THAN, 0); }
		public LessThanContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitLessThan(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class FormatContext extends ExprContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public FormatContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitFormat(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class NotEqualContext extends ExprContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode NOT_EQUAL() { return getToken(FormatterParser.NOT_EQUAL, 0); }
		public NotEqualContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitNotEqual(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class TernaryContext extends ExprContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public List<ParseContext> parse() {
			return getRuleContexts(ParseContext.class);
		}
		public ParseContext parse(int i) {
			return getRuleContext(ParseContext.class,i);
		}
		public TernaryContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitTernary(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class EqualContext extends ExprContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode EQUAL() { return getToken(FormatterParser.EQUAL, 0); }
		public EqualContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitEqual(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class IDContext extends ExprContext {
		public TerminalNode ID() { return getToken(FormatterParser.ID, 0); }
		public IDContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitID(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class StringContext extends ExprContext {
		public TerminalNode STRING() { return getToken(FormatterParser.STRING, 0); }
		public StringContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitString(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class MoreThanContext extends ExprContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode MORE_THAN() { return getToken(FormatterParser.MORE_THAN, 0); }
		public MoreThanContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitMoreThan(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class MoreThanEqualContext extends ExprContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode MORE_THAN_EQUAL() { return getToken(FormatterParser.MORE_THAN_EQUAL, 0); }
		public MoreThanEqualContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitMoreThanEqual(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class LessThanEqualContext extends ExprContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode LESS_THAN_EQUAL() { return getToken(FormatterParser.LESS_THAN_EQUAL, 0); }
		public LessThanEqualContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitLessThanEqual(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExprContext expr() throws RecognitionException {
		return expr(0);
	}

	private ExprContext expr(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		ExprContext _localctx = new ExprContext(_ctx, _parentState);
		ExprContext _prevctx = _localctx;
		int _startState = 2;
		enterRecursionRule(_localctx, 2, RULE_expr, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(26);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
			case 1:
				{
				_localctx = new TernaryContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(10);
				match(T__0);
				setState(11);
				expr(0);
				setState(12);
				match(T__1);
				setState(13);
				parse();
				setState(16);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__2) {
					{
					setState(14);
					match(T__2);
					setState(15);
					parse();
					}
				}

				setState(18);
				match(T__3);
				}
				break;
			case 2:
				{
				_localctx = new FormatContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(20);
				match(T__0);
				setState(21);
				expr(0);
				setState(22);
				match(T__3);
				}
				break;
			case 3:
				{
				_localctx = new IDContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(24);
				match(ID);
				}
				break;
			case 4:
				{
				_localctx = new StringContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(25);
				match(STRING);
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(48);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(46);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
					case 1:
						{
						_localctx = new EqualContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(28);
						if (!(precpred(_ctx, 8))) throw new FailedPredicateException(this, "precpred(_ctx, 8)");
						setState(29);
						match(EQUAL);
						setState(30);
						expr(9);
						}
						break;
					case 2:
						{
						_localctx = new NotEqualContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(31);
						if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
						setState(32);
						match(NOT_EQUAL);
						setState(33);
						expr(8);
						}
						break;
					case 3:
						{
						_localctx = new MoreThanContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(34);
						if (!(precpred(_ctx, 6))) throw new FailedPredicateException(this, "precpred(_ctx, 6)");
						setState(35);
						match(MORE_THAN);
						setState(36);
						expr(7);
						}
						break;
					case 4:
						{
						_localctx = new MoreThanEqualContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(37);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(38);
						match(MORE_THAN_EQUAL);
						setState(39);
						expr(6);
						}
						break;
					case 5:
						{
						_localctx = new LessThanContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(40);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(41);
						match(LESS_THAN);
						setState(42);
						expr(5);
						}
						break;
					case 6:
						{
						_localctx = new LessThanEqualContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(43);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(44);
						match(LESS_THAN_EQUAL);
						setState(45);
						expr(4);
						}
						break;
					}
					} 
				}
				setState(50);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 1:
			return expr_sempred((ExprContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expr_sempred(ExprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 8);
		case 1:
			return precpred(_ctx, 7);
		case 2:
			return precpred(_ctx, 6);
		case 3:
			return precpred(_ctx, 5);
		case 4:
			return precpred(_ctx, 4);
		case 5:
			return precpred(_ctx, 3);
		}
		return true;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3\16\66\4\2\t\2\4\3"+
		"\t\3\3\2\6\2\b\n\2\r\2\16\2\t\3\3\3\3\3\3\3\3\3\3\3\3\3\3\5\3\23\n\3\3"+
		"\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\5\3\35\n\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3"+
		"\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\7\3\61\n\3\f\3\16\3\64\13"+
		"\3\3\3\2\3\4\4\2\4\2\2\2>\2\7\3\2\2\2\4\34\3\2\2\2\6\b\5\4\3\2\7\6\3\2"+
		"\2\2\b\t\3\2\2\2\t\7\3\2\2\2\t\n\3\2\2\2\n\3\3\2\2\2\13\f\b\3\1\2\f\r"+
		"\7\3\2\2\r\16\5\4\3\2\16\17\7\4\2\2\17\22\5\2\2\2\20\21\7\5\2\2\21\23"+
		"\5\2\2\2\22\20\3\2\2\2\22\23\3\2\2\2\23\24\3\2\2\2\24\25\7\6\2\2\25\35"+
		"\3\2\2\2\26\27\7\3\2\2\27\30\5\4\3\2\30\31\7\6\2\2\31\35\3\2\2\2\32\35"+
		"\7\16\2\2\33\35\7\r\2\2\34\13\3\2\2\2\34\26\3\2\2\2\34\32\3\2\2\2\34\33"+
		"\3\2\2\2\35\62\3\2\2\2\36\37\f\n\2\2\37 \7\7\2\2 \61\5\4\3\13!\"\f\t\2"+
		"\2\"#\7\b\2\2#\61\5\4\3\n$%\f\b\2\2%&\7\t\2\2&\61\5\4\3\t\'(\f\7\2\2("+
		")\7\n\2\2)\61\5\4\3\b*+\f\6\2\2+,\7\13\2\2,\61\5\4\3\7-.\f\5\2\2./\7\f"+
		"\2\2/\61\5\4\3\6\60\36\3\2\2\2\60!\3\2\2\2\60$\3\2\2\2\60\'\3\2\2\2\60"+
		"*\3\2\2\2\60-\3\2\2\2\61\64\3\2\2\2\62\60\3\2\2\2\62\63\3\2\2\2\63\5\3"+
		"\2\2\2\64\62\3\2\2\2\7\t\22\34\60\62";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}