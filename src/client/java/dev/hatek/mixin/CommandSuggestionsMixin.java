package dev.hatek.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.hatek.client.feature.command.Commands;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.commands.SharedSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public class CommandSuggestionsMixin {
    /** A chat line that starts with a dot gets Hatek's command and nickname suggestions instead of the tab-list ones. */
    @Redirect(
            method = "updateCommandInfo",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/SharedSuggestionProvider;suggest(Ljava/lang/Iterable;"
                            + "Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Suggestions> hatek$suggest(Iterable<String> candidates, SuggestionsBuilder builder) {
        if (builder.getInput().startsWith(".")) {
            return CompletableFuture.completedFuture(Commands.suggestions(builder));
        }
        return SharedSuggestionProvider.suggest(candidates, builder);
    }
}
