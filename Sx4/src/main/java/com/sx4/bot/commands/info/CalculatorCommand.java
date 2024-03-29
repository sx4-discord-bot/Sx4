package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.antlr.CalcEvalVisitor;
import com.sx4.bot.antlr.CalcLexer;
import com.sx4.bot.antlr.CalcParser;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.ExceptionUtility;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.text.DecimalFormat;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CalculatorCommand extends Sx4Command {

    public CalculatorCommand() {
        super("calculator", 29);

        super.setAliases("calc");
        super.setDescription("Eval a mathematical equation");
        super.setExamples("calc 1 + 1", "calc a = 5; a + 10");
        super.setExecuteAsync(true);
        super.setCategoryAll(ModuleCategory.INFORMATION);
    }

    public void onCommand(Sx4CommandEvent event, @Argument(value="expression", endless=true) String expression, @Option(value="pretty", description="Puts a comma every 3 digits before the decimal point") boolean pretty) {
        CalcLexer lexer = new CalcLexer(CharStreams.fromString(expression));
        CalcParser parser = new CalcParser(new CommonTokenStream(lexer));

        new CalcEvalVisitor().parse(parser.parse()).orTimeout(100, TimeUnit.MILLISECONDS).whenComplete((result, exception) -> {
           Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
           if (cause instanceof TimeoutException) {
               event.reply("That expression took longer than 100ms to execute :stopwatch:").queue();
               return;
           }

           if (ExceptionUtility.sendExceptionally(event, exception)) {
               return;
           }

            DecimalFormat format = new DecimalFormat((pretty ? ",##" : "") + "0.##########");
            event.reply(format.format(result)).queue();
        });
    }

}
