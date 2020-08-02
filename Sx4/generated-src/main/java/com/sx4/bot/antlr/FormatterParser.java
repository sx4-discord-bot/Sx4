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
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, EQUAL=12, NOT_EQUAL=13, MORE_THAN=14, MORE_THAN_EQUAL=15, 
		LESS_THAN=16, LESS_THAN_EQUAL=17, AND=18, OR=19, STRING=20, ID=21;
	public static final int
		RULE_parse = 0, RULE_expr = 1, RULE_condition = 2;
	private static String[] makeRuleNames() {
		return new String[] {
			"parse", "expr", "condition"
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
			setState(7); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(6);
				expr(0);
				}
				}
				setState(9); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__6) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << STRING) | (1L << ID))) != 0) );
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
	public static class UpperContext extends ExprContext {
		public ParseContext parse() {
			return getRuleContext(ParseContext.class,0);
		}
		public UpperContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitUpper(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class CondContext extends ExprContext {
		public List<ConditionContext> condition() {
			return getRuleContexts(ConditionContext.class);
		}
		public ConditionContext condition(int i) {
			return getRuleContext(ConditionContext.class,i);
		}
		public CondContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitCond(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class LowerContext extends ExprContext {
		public ParseContext parse() {
			return getRuleContext(ParseContext.class,0);
		}
		public LowerContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitLower(this);
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
	public static class TitleContext extends ExprContext {
		public ParseContext parse() {
			return getRuleContext(ParseContext.class,0);
		}
		public TitleContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitTitle(this);
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
	public static class EmptyContext extends ExprContext {
		public EmptyContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitEmpty(this);
			else return visitor.visitChildren(this);
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
	public static class TernaryPyContext extends ExprContext {
		public List<ParseContext> parse() {
			return getRuleContexts(ParseContext.class);
		}
		public ParseContext parse(int i) {
			return getRuleContext(ParseContext.class,i);
		}
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TernaryPyContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitTernaryPy(this);
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
			setState(60);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,4,_ctx) ) {
			case 1:
				{
				_localctx = new TernaryContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(12);
				match(T__0);
				setState(13);
				expr(0);
				setState(14);
				match(T__1);
				setState(15);
				parse();
				setState(18);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__2) {
					{
					setState(16);
					match(T__2);
					setState(17);
					parse();
					}
				}

				setState(20);
				match(T__3);
				}
				break;
			case 2:
				{
				_localctx = new TernaryPyContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(22);
				match(T__0);
				setState(23);
				parse();
				setState(24);
				match(T__4);
				setState(25);
				expr(0);
				setState(28);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__5) {
					{
					setState(26);
					match(T__5);
					setState(27);
					parse();
					}
				}

				setState(30);
				match(T__3);
				}
				break;
			case 3:
				{
				_localctx = new FormatContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(32);
				match(T__6);
				setState(33);
				expr(0);
				setState(34);
				match(T__7);
				}
				break;
			case 4:
				{
				_localctx = new EmptyContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(36);
				match(T__6);
				setState(37);
				match(T__7);
				}
				break;
			case 5:
				{
				_localctx = new CondContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(38);
				match(T__0);
				setState(40); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(39);
					condition();
					}
					}
					setState(42); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__6) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << AND) | (1L << OR) | (1L << STRING) | (1L << ID))) != 0) );
				setState(44);
				match(T__3);
				}
				break;
			case 6:
				{
				_localctx = new UpperContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(46);
				match(T__8);
				setState(47);
				parse();
				setState(48);
				match(T__3);
				}
				break;
			case 7:
				{
				_localctx = new LowerContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(50);
				match(T__9);
				setState(51);
				parse();
				setState(52);
				match(T__3);
				}
				break;
			case 8:
				{
				_localctx = new TitleContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(54);
				match(T__10);
				setState(55);
				parse();
				setState(56);
				match(T__3);
				}
				break;
			case 9:
				{
				_localctx = new IDContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(58);
				match(ID);
				}
				break;
			case 10:
				{
				_localctx = new StringContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(59);
				match(STRING);
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(82);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,6,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(80);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,5,_ctx) ) {
					case 1:
						{
						_localctx = new EqualContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(62);
						if (!(precpred(_ctx, 11))) throw new FailedPredicateException(this, "precpred(_ctx, 11)");
						setState(63);
						match(EQUAL);
						setState(64);
						expr(12);
						}
						break;
					case 2:
						{
						_localctx = new NotEqualContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(65);
						if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
						setState(66);
						match(NOT_EQUAL);
						setState(67);
						expr(11);
						}
						break;
					case 3:
						{
						_localctx = new MoreThanContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(68);
						if (!(precpred(_ctx, 9))) throw new FailedPredicateException(this, "precpred(_ctx, 9)");
						setState(69);
						match(MORE_THAN);
						setState(70);
						expr(10);
						}
						break;
					case 4:
						{
						_localctx = new MoreThanEqualContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(71);
						if (!(precpred(_ctx, 8))) throw new FailedPredicateException(this, "precpred(_ctx, 8)");
						setState(72);
						match(MORE_THAN_EQUAL);
						setState(73);
						expr(9);
						}
						break;
					case 5:
						{
						_localctx = new LessThanContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(74);
						if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
						setState(75);
						match(LESS_THAN);
						setState(76);
						expr(8);
						}
						break;
					case 6:
						{
						_localctx = new LessThanEqualContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(77);
						if (!(precpred(_ctx, 6))) throw new FailedPredicateException(this, "precpred(_ctx, 6)");
						setState(78);
						match(LESS_THAN_EQUAL);
						setState(79);
						expr(7);
						}
						break;
					}
					} 
				}
				setState(84);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,6,_ctx);
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

	public static class ConditionContext extends ParserRuleContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode AND() { return getToken(FormatterParser.AND, 0); }
		public TerminalNode OR() { return getToken(FormatterParser.OR, 0); }
		public ConditionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_condition; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FormatterVisitor ) return ((FormatterVisitor<? extends T>)visitor).visitCondition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ConditionContext condition() throws RecognitionException {
		ConditionContext _localctx = new ConditionContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_condition);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(86);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__6) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << STRING) | (1L << ID))) != 0)) {
				{
				setState(85);
				expr(0);
				}
			}

			setState(88);
			((ConditionContext)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !(_la==AND || _la==OR) ) {
				((ConditionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(89);
			expr(0);
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
			return precpred(_ctx, 11);
		case 1:
			return precpred(_ctx, 10);
		case 2:
			return precpred(_ctx, 9);
		case 3:
			return precpred(_ctx, 8);
		case 4:
			return precpred(_ctx, 7);
		case 5:
			return precpred(_ctx, 6);
		}
		return true;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3\27^\4\2\t\2\4\3\t"+
		"\3\4\4\t\4\3\2\6\2\n\n\2\r\2\16\2\13\3\3\3\3\3\3\3\3\3\3\3\3\3\3\5\3\25"+
		"\n\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\5\3\37\n\3\3\3\3\3\3\3\3\3\3\3\3"+
		"\3\3\3\3\3\3\3\3\3\6\3+\n\3\r\3\16\3,\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3"+
		"\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\5\3?\n\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3"+
		"\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\7\3S\n\3\f\3\16\3V\13\3\3"+
		"\4\5\4Y\n\4\3\4\3\4\3\4\3\4\2\3\4\5\2\4\6\2\3\3\2\24\25\2n\2\t\3\2\2\2"+
		"\4>\3\2\2\2\6X\3\2\2\2\b\n\5\4\3\2\t\b\3\2\2\2\n\13\3\2\2\2\13\t\3\2\2"+
		"\2\13\f\3\2\2\2\f\3\3\2\2\2\r\16\b\3\1\2\16\17\7\3\2\2\17\20\5\4\3\2\20"+
		"\21\7\4\2\2\21\24\5\2\2\2\22\23\7\5\2\2\23\25\5\2\2\2\24\22\3\2\2\2\24"+
		"\25\3\2\2\2\25\26\3\2\2\2\26\27\7\6\2\2\27?\3\2\2\2\30\31\7\3\2\2\31\32"+
		"\5\2\2\2\32\33\7\7\2\2\33\36\5\4\3\2\34\35\7\b\2\2\35\37\5\2\2\2\36\34"+
		"\3\2\2\2\36\37\3\2\2\2\37 \3\2\2\2 !\7\6\2\2!?\3\2\2\2\"#\7\t\2\2#$\5"+
		"\4\3\2$%\7\n\2\2%?\3\2\2\2&\'\7\t\2\2\'?\7\n\2\2(*\7\3\2\2)+\5\6\4\2*"+
		")\3\2\2\2+,\3\2\2\2,*\3\2\2\2,-\3\2\2\2-.\3\2\2\2./\7\6\2\2/?\3\2\2\2"+
		"\60\61\7\13\2\2\61\62\5\2\2\2\62\63\7\6\2\2\63?\3\2\2\2\64\65\7\f\2\2"+
		"\65\66\5\2\2\2\66\67\7\6\2\2\67?\3\2\2\289\7\r\2\29:\5\2\2\2:;\7\6\2\2"+
		";?\3\2\2\2<?\7\27\2\2=?\7\26\2\2>\r\3\2\2\2>\30\3\2\2\2>\"\3\2\2\2>&\3"+
		"\2\2\2>(\3\2\2\2>\60\3\2\2\2>\64\3\2\2\2>8\3\2\2\2><\3\2\2\2>=\3\2\2\2"+
		"?T\3\2\2\2@A\f\r\2\2AB\7\16\2\2BS\5\4\3\16CD\f\f\2\2DE\7\17\2\2ES\5\4"+
		"\3\rFG\f\13\2\2GH\7\20\2\2HS\5\4\3\fIJ\f\n\2\2JK\7\21\2\2KS\5\4\3\13L"+
		"M\f\t\2\2MN\7\22\2\2NS\5\4\3\nOP\f\b\2\2PQ\7\23\2\2QS\5\4\3\tR@\3\2\2"+
		"\2RC\3\2\2\2RF\3\2\2\2RI\3\2\2\2RL\3\2\2\2RO\3\2\2\2SV\3\2\2\2TR\3\2\2"+
		"\2TU\3\2\2\2U\5\3\2\2\2VT\3\2\2\2WY\5\4\3\2XW\3\2\2\2XY\3\2\2\2YZ\3\2"+
		"\2\2Z[\t\2\2\2[\\\5\4\3\2\\\7\3\2\2\2\n\13\24\36,>RTX";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}