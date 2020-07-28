package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.antlr.CalcEvalVisitor;
import com.sx4.bot.antlr.CalcLexer;
import com.sx4.bot.antlr.CalcParser;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class CalculatorCommand extends Sx4Command {

    public CalculatorCommand() {
        super("calculator");

        super.setAliases("calc");
        super.setDescription("Eval a mathematical equation");
        super.setExamples("calc 1 + 1", "calc a = 5; a + 10");
    }

    public void onCommand(Sx4CommandEvent event, @Argument(value="expression", endless=true) String expression) {
        CalcLexer lexer = new CalcLexer(CharStreams.fromString(expression));
        CalcParser parser = new CalcParser(new CommonTokenStream(lexer));

        event.reply(String.valueOf(new CalcEvalVisitor().visit(parser.parse()))).queue();
    }

}
