package cn.paper_card.paper_card_tab;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public final class PaperCardTab extends JavaPlugin implements Listener {

    private long startTime = -1;
    private long totalTime = 0;
    private int ticks = 0;

    private double mspt = 0;

    private void updateTab() {
        final TextComponent header = Component.text()
                .append(Component.text("Paper Card Server").color(NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .build();


        for (final Player player : this.getServer().getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            final World world = player.getWorld();
            final long days = (world.getGameTime() / 24000L) + 1;

            final TextComponent footer = Component.text()
                    .append(Component.text("Ping: %d    MSPT: %.2f".formatted(player.getPing(), this.mspt)))
                    .append(Component.newline())
                    .append(Component.text("在线人数: %d/%d".formatted(
                            this.getServer().getOnlinePlayers().size(),
                            this.getServer().getOfflinePlayers().length))
                    )
                    .append(Component.newline())
                    .append(Component.text("游戏里的第"))
                    .append(Component.text(days).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
                    .append(Component.text("天"))
                    .build();


            player.sendPlayerListHeader(header);
            player.sendPlayerListFooter(footer);
            player.playerListName(Component.text()
                    .append(player.displayName())
                    .append(Component.space())
                    .append(Component.text(player.getPing()).color(NamedTextColor.YELLOW))
                    .build());
        }
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        this.updateTab();
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        this.updateTab();
    }

    @EventHandler
    public void on1(@NotNull ServerTickStartEvent event) {
        this.startTime = System.currentTimeMillis();
    }

    @EventHandler
    public void on2(@NotNull ServerTickEndEvent event) {

        this.totalTime += System.currentTimeMillis() - this.startTime;

        ++this.ticks;

        if (this.ticks == 20) {
            this.mspt = (double) this.totalTime / this.ticks;
            this.ticks = 0;
            this.totalTime = 0;
        }
    }


    @Override
    public void onEnable() {
        this.getServer().getAsyncScheduler().runAtFixedRate(this, task -> updateTab(), 1, 1, TimeUnit.SECONDS);
        this.getServer().getPluginManager().registerEvents(this, this);
    }

}
