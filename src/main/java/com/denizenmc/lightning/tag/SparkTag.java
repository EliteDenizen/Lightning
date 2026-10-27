package com.denizenmc.lightning.tag;

import com.denizenmc.lightning.charge.ChargeState;
import com.denizenmc.lightning.spark.Spark;
import com.denizenmc.lightning.spark.Sparks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

// Resolves reusable Sparks into their configured components.
public final class SparkTag {

    private SparkTag() {
    }

    public static @NotNull TagResolver resolver(@NotNull final Sparks sparks) {
        return TagResolver.resolver("spark", (args, context) -> {
            String name = args.popOr("A spark tag needs a name, like <spark:prefix>").lowerValue();
            Spark spark = sparks.get(name);
            ChargeState state = ChargeState.current();
            if (spark == null || (state != null && !state.spendSpark())) return Tag.selfClosingInserting(Component.empty());

            List<String> values = new ArrayList<>(2);
            while (args.hasNext()) values.add(args.pop().value());
            return spark.render(values, state);
        });
    }
}
