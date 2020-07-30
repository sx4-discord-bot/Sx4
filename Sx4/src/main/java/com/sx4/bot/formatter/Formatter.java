package com.sx4.bot.formatter;

import com.sx4.bot.antlr.FormatterEvalVisitor;
import com.sx4.bot.antlr.FormatterLexer;
import com.sx4.bot.antlr.FormatterParser;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.HashMap;
import java.util.Map;

public class Formatter {

    private final Map<String, Object> map;
    private String string;

    public Formatter(String string) {
        this(string, new HashMap<>());
    }

    private Formatter(String string, Map<String, Object> map) {
        this.string = string;
        this.map = map;
    }

    public Formatter append(String key, Object replace) {
        this.map.put(key, replace);

        return this;
    }

    public Formatter user(User user) {
        return this.append("user.mention", user.getAsMention())
            .append("user.name", user.getName())
            .append("user.id", user.getId())
            .append("user.discriminator", user.getDiscriminator())
            .append("user.tag", user.getAsTag());
    }

    public Formatter channel(TextChannel channel) {
        return this.append("channel.mention", channel.getAsMention())
            .append("channel.name", channel.getName())
            .append("channel.id", channel.getId());
    }

    public String format() {
        return this.format(this.string, this.map);
    }
    
    private String format(String string, Map<String, Object> map) {
        FormatterLexer lexer = new FormatterLexer(CharStreams.fromString(string));
        FormatterParser parser = new FormatterParser(new CommonTokenStream(lexer));

        return new FormatterEvalVisitor(map).visit(parser.parse());
    }

    public static Formatter of(String string, Map<String, Object> map) {
        return new Formatter(string, map);
    }

}
