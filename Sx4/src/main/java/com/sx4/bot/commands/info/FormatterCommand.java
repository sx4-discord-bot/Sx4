package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.formatter.output.FormatterManager;
import com.sx4.bot.formatter.output.function.FormatterFunction;
import com.sx4.bot.formatter.output.function.FormatterVariable;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ClassUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FormatterCommand extends Sx4Command {

	public FormatterCommand() {
		super("formatter", 492);

		super.setDescription("Get information on a formatter type, variable or function");
		super.setExamples("formatter", "formatter FreeGame", "formatter FreeGame.platform");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	private String getReturnTypeString(Type returnType) {
		if (!(returnType instanceof ParameterizedType type)) {
			return ((Class<?>) returnType).getSimpleName();
		}

		Class<?> clazz = (Class<?>) type.getRawType();
		Type[] types = ClassUtility.getParameterTypes(type, clazz);

		return clazz.getSimpleName() + "<" + Arrays.stream(types).map(this::getReturnTypeString).collect(Collectors.joining(", ")) + ">";
	}

	private String getFunctionString(FormatterFunction<?> function) {
		String parameters = Arrays.stream(function.getMethod().getParameters())
			.skip(1)
			.map(Parameter::getType)
			.map(Class::getSimpleName)
			.collect(Collectors.joining(", "));

		return function.getName() + "(" + parameters + ")";
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="arguments", nullDefault=true) String content) {
		FormatterManager manager = FormatterManager.getDefaultManager();

		String[] arguments = content == null ? new String[0] : content.split("\\.");

		Set<Class<?>> functionClasses = manager.getFunctions().keySet();
		Set<Class<?>> variableClasses = manager.getVariables().keySet();

		Set<Class<?>> classes = new HashSet<>();
		classes.addAll(functionClasses);
		classes.addAll(variableClasses);

		List<Class<?>> filteredClasses = classes.stream()
			.filter(clazz -> arguments.length == 0 || arguments[0].equalsIgnoreCase(clazz.getSimpleName()))
			.collect(Collectors.toList());

		if (filteredClasses.isEmpty()) {
			event.replyFailure("I could not find that formatter type").queue();
			return;
		}

		PagedResult<Class<?>> paged = new PagedResult<>(event.getBot(), filteredClasses)
			.setPerPage(15)
			.setAutoSelect(true)
			.setDisplayFunction(Class::getSimpleName);

		paged.onSelect(select -> {
			Class<?> clazz = select.getSelected();
			for (int i = 1; i < arguments.length; i++) {
				String name = arguments[i];

				FormatterFunction<?> function = manager.getFunctions(clazz).stream()
					.filter(f -> f.getName().equalsIgnoreCase(name))
					.findFirst()
					.orElse(null);

				FormatterVariable<?> variable = manager.getVariables(clazz).stream()
					.filter(v -> v.getName().equalsIgnoreCase(name))
					.findFirst()
					.orElse(null);

				boolean last = i == arguments.length - 1;

				MessageEmbed embed;
				if (function != null) {
					Method method = function.getMethod();
					if (!last) {
						clazz = method.getReturnType();
						continue;
					}

					embed = new EmbedBuilder()
						.setDescription(function.getDescription())
						.setTitle(this.getFunctionString(function))
						.addField("Return Type", this.getReturnTypeString(method.getGenericReturnType()), true)
						.build();
				} else if (variable != null) {
					Type returnType = variable.getReturnType();
					if (!last) {
						clazz = (Class<?>) returnType;
						continue;
					}

					embed = new EmbedBuilder()
						.setDescription(variable.getDescription())
						.setTitle(variable.getName())
						.addField("Return Type", this.getReturnTypeString(returnType), true)
						.build();
				} else {
					event.replyFailure("I could not find a variable or function named `" + name + "` on the type `" + clazz.getSimpleName() + "`").queue();
					return;
				}

				event.reply(embed).queue();
				return;
			}

			String variables = manager.getVariables(clazz).stream()
				.map(FormatterVariable::getName)
				.collect(Collectors.joining("\n"));

			String functions = manager.getFunctions(clazz).stream()
				.map(this::getFunctionString)
				.collect(Collectors.joining("\n"));

			EmbedBuilder embed = new EmbedBuilder()
				.setTitle(clazz.getSimpleName())
				.addField("Functions", functions.isEmpty() ? "None" : functions, true)
				.addField("Variables", variables.isEmpty() ? "None" : variables, true);

			event.reply(embed.build()).queue();
		});

		paged.execute(event);
	}

}
