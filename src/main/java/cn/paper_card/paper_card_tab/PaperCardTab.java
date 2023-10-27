package cn.paper_card.paper_card_tab;

import cn.paper_card.paper_card_afk.PaperCardAfkApi;
import cn.paper_card.paper_card_tip.PaperCardTipApi;
import cn.paper_card.view_distance.ViewDistanceApi;
import io.papermc.paper.threadedregions.TickData;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import me.lucko.spark.api.Spark;
import me.lucko.spark.api.statistic.StatisticWindow;
import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;
import me.lucko.spark.api.statistic.types.DoubleStatistic;
import me.lucko.spark.api.statistic.types.GenericStatistic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class PaperCardTab extends JavaPlugin implements Listener {

    private ViewDistanceApi viewDistanceApi = null;
    private PaperCardTipApi paperCardTipApi = null;

    private final @NotNull PaperCardAfkApi paperCardAfkApi;

    private long lastTipTime = -1;
    private TextComponent lastTip = null;

    private Spark spark = null;

    public PaperCardTab() {
        this.paperCardAfkApi = this.getPaperCardAfkApi0();
    }

    private @NotNull PaperCardAfkApi getPaperCardAfkApi0() {
        final Plugin plugin = this.getServer().getPluginManager().getPlugin("PaperCardAfk");
        if (plugin instanceof final PaperCardAfkApi api) {
            return api;
        } else throw new NoSuchElementException("PaperCardAfk插件未安装！");
    }

    private @NotNull TextComponent buildTip(@NotNull PaperCardTipApi.Tip tip) {
        final TextComponent.Builder text = Component.text();
        text.append(Component.text("[你知道吗？#%d]".formatted(tip.id())).color(NamedTextColor.GREEN));
        text.appendSpace();
        text.append(Component.text(tip.content()).color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        text.append(Component.text("  --"));
        text.append(Component.text(tip.category()).color(NamedTextColor.GOLD).decorate(TextDecoration.ITALIC));
        return text.build();
    }

    private void updateTab() {


        final TextComponent.Builder header = Component.text()
                .append(Component.text("纸 ").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD).decorate(TextDecoration.ITALIC))
                .append(Component.text("Paper Card").color(NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD))
                .append(Component.space())
                .append(Component.text(" 片").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD).decorate(TextDecoration.ITALIC));
        header.appendNewline();


        header.append(Component.text(("在线: %d" +
                "/%d").formatted(
                this.getServer().getOnlinePlayers().size(),
                this.getServer().getOfflinePlayers().length))
        );

        if (this.spark != null) {
            final DoubleStatistic<StatisticWindow.CpuUsage> cpuUsageDoubleStatistic = this.spark.cpuProcess();
            final double cpu = cpuUsageDoubleStatistic.poll(StatisticWindow.CpuUsage.SECONDS_10) * 100;
            header.append(Component.text("  "));
            header.append(Component.text("CPU: %.2f%%".formatted(cpu)));

            final GenericStatistic<DoubleAverageInfo, StatisticWindow.MillisPerTick> mspt = this.spark.mspt();
            if (mspt != null) {
                final DoubleAverageInfo poll = mspt.poll(StatisticWindow.MillisPerTick.SECONDS_10);
                final double mean = poll.mean();

                header.append(Component.text("  "));
                header.append(Component.text("MSPT: %.2f".formatted(mean)));
            }
        }

        header.appendNewline();

        final long current = System.currentTimeMillis();

        // 你知道吗
        if (this.lastTipTime > 0 && current < this.lastTipTime + 20 * 1000L) {
            if (this.lastTip != null) {
                header.appendNewline();
                header.append(this.lastTip);
                header.appendNewline();
            }
        } else {
            if (this.paperCardTipApi != null) {
                try {
                    final int count = this.paperCardTipApi.queryCount();
                    if (count > 0) {
                        int i = this.getTipIndex();
                        i %= count;

                        final List<PaperCardTipApi.Tip> list = this.paperCardTipApi.queryByPage(1, i);
                        final int size = list.size();
                        if (size == 1) {
                            final PaperCardTipApi.Tip tip = list.get(0);
                            this.lastTip = this.buildTip(tip);
                            this.lastTipTime = current;
                            header.appendNewline();
                            header.append(this.lastTip);
                            header.appendNewline();
                        }

                        i += 1;
                        i %= count;
                        this.setTipIndex(i);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // 计算平均Ping
        long totalPing = 0;
        int c = 0;
        for (final Player player : this.getServer().getOnlinePlayers()) {
            totalPing += player.getPing();
            ++c;
        }

        final double avgPing = (double) totalPing / c;
        for (final Player player : this.getServer().getOnlinePlayers()) {

            player.sendPlayerListHeader(header.build());

            player.getScheduler().run(this, task -> {

                final TickData.TickReportData report = TickRegionScheduler.getCurrentRegion()
                        .getData()
                        .getRegionSchedulingHandle()
                        .getTickReport5s(System.nanoTime());


                final double mspt = report.timePerTickData().segmentAll().average() / 1.0E6;
                final double tps = report.tpsData().segmentAll().average();

                final int ping = player.getPing();

                if (this.viewDistanceApi != null) {
                    this.viewDistanceApi.tryAdjust(player, mspt, ping, avgPing);
                }


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


                player.sendPlayerListFooter(footer);

                final TextComponent.Builder listName = Component.text();
                listName.append(player.displayName());
                listName.appendSpace();

                final PaperCardAfkApi.AfkPlayer afkPlayer = this.paperCardAfkApi.getAfkPlayer(player.getUniqueId());
                if (afkPlayer != null) {
                    if (afkPlayer.getAfkSince() > 0) {
                        final String msg = afkPlayer.getAfkMessage();
                        listName.append(Component.text(Objects.requireNonNullElse(msg, "?")).color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
                        listName.appendSpace();
                    }
                }
                listName.append(Component.text(ping).color(pingColor));

                player.playerListName(listName.build());
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

    private int getTipIndex() {
        return this.getConfig().getInt("tip-index", 0);
    }

    private void setTipIndex(int i) {
        this.getConfig().set("tip-index", i);
    }


    @Override
    public void onEnable() {
        this.getServer().getAsyncScheduler().runAtFixedRate(this, task -> updateTab(), 1, 1, TimeUnit.SECONDS);
        this.getServer().getPluginManager().registerEvents(this, this);

        final Plugin plugin = this.getServer().getPluginManager().getPlugin("ViewDistance");
        if (plugin instanceof ViewDistanceApi api) {
            this.viewDistanceApi = api;
        }

        final Plugin plugin1 = this.getServer().getPluginManager().getPlugin("PaperCardTip");
        if (plugin1 instanceof PaperCardTipApi api) {
            this.paperCardTipApi = api;
        }

        this.setTipIndex(this.getTipIndex());
        this.saveConfig();

        final RegisteredServiceProvider<Spark> registration = this.getServer().getServicesManager().getRegistration(Spark.class);
        if (registration != null) {
            this.spark = registration.getProvider();
            this.getLogger().info("已链接到SparkAPI");
        } else {
            this.getLogger().warning("无法访问SparkAPI！");
        }
    }

    @Override
    public void onDisable() {
        this.saveConfig();
    }
}
