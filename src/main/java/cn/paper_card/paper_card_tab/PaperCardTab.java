package cn.paper_card.paper_card_tab;

import io.papermc.paper.threadedregions.TickData;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public final class PaperCardTab extends JavaPlugin implements Listener {

    private void updateTab() {
        final TextComponent header = Component.text()
                .append(Component.text("纸 ").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD).decorate(TextDecoration.ITALIC))
                .append(Component.text("Paper Card").color(NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD))
                .append(Component.space())
                .append(Component.text(" 片").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD).decorate(TextDecoration.ITALIC))
                .append(Component.newline())
                .build();


        // 计算平均Ping
        long totalPing = 0;
        int c = 0;
        for (final Player player : this.getServer().getOnlinePlayers()) {
            totalPing += player.getPing();
            ++c;
        }
        final double avgPing = (double) totalPing / c;

        final long current = System.currentTimeMillis();

        for (final Player player : this.getServer().getOnlinePlayers()) {

            player.getScheduler().run(this, task -> {

                final TickData.TickReportData report = TickRegionScheduler.getCurrentRegion()
                        .getData()
                        .getRegionSchedulingHandle()
                        .getTickReport5s(System.nanoTime());

                final double mspt = report.timePerTickData().segmentAll().average() / 1.0E6;

                final double tps = mspt < 50 ? 20 : 1000.0 / mspt;

                final int ping = player.getPing();


                // MSPT显示颜色
                final NamedTextColor msptColor;
                if (mspt < 50) msptColor = NamedTextColor.GREEN;
                else if (mspt < 55) msptColor = NamedTextColor.GOLD;
                else msptColor = NamedTextColor.RED;

                final NamedTextColor pingColor;
                if (ping < 200) pingColor = NamedTextColor.GREEN;
                else if (ping < 500) pingColor = NamedTextColor.GOLD;
                else pingColor = NamedTextColor.RED;

                final NamedTextColor avgPingColor;
                if (avgPing < 200) avgPingColor = NamedTextColor.GREEN;
                else if (avgPing < 500) avgPingColor = NamedTextColor.GOLD;
                else avgPingColor = NamedTextColor.RED;


                long dayNo = current - player.getFirstPlayed();
                dayNo /= (1000L * 60 * 60 * 24);
                dayNo += 1;

                final TextComponent footer = Component.text()
                        .append(Component.text("AvgPing: "))
                        .append(Component.text("%.2f".formatted(avgPing)).color(avgPingColor))
                        .append(Component.text("  "))
                        .append(Component.text("Ping: "))
                        .append(Component.text("%d".formatted(ping)).color(pingColor))
                        .append(Component.text("  "))
                        .append(Component.text("TPS: "))
                        .append(Component.text("%.2f".formatted(tps)).color(msptColor))
                        .append(Component.text("  "))
                        .append(Component.text("MSPT: "))
                        .append(Component.text("%.2f".formatted(mspt)).color(msptColor))
                        .append(Component.newline())

                        .append(Component.text(("在线: %d" +
                                "/%d").formatted(
                                this.getServer().getOnlinePlayers().size(),
                                this.getServer().getOfflinePlayers().length))
                        )
                        .append(Component.text("    "))
                        .append(Component.text("视距：%d  模拟：%d  发送：%d".formatted(
                                player.getViewDistance(),
                                player.getSimulationDistance(),
                                player.getSendViewDistance()
                        )))
                        .appendNewline()

                        .append(Component.text("这是你游玩服务器的第"))
                        .append(Component.text(dayNo).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
                        .append(Component.text("天"))
                        .build();

                player.sendPlayerListHeader(header);
                player.sendPlayerListFooter(footer);
                player.playerListName(Component.text()
                        .append(player.displayName())
                        .append(Component.space())
                        .append(Component.text(player.getPing()).color(NamedTextColor.YELLOW))
                        .build());

            }, null);


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


    @Override
    public void onEnable() {
        this.getServer().getAsyncScheduler().runAtFixedRate(this, task -> updateTab(), 1, 1, TimeUnit.SECONDS);
        this.getServer().getPluginManager().registerEvents(this, this);
    }

}
