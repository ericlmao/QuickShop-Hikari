package com.ghostchu.quickshop.shop;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.economy.AbstractEconomy;
import com.ghostchu.quickshop.api.event.economy.ShopSuccessPurchaseEvent;
import com.ghostchu.quickshop.api.inventory.InventoryWrapper;
import com.ghostchu.quickshop.api.localization.text.ProxiedLocale;
import com.ghostchu.quickshop.api.localization.text.Text;
import com.ghostchu.quickshop.api.localization.text.TextManager;
import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.api.shop.Info;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.economy.SimpleEconomyTransaction;
import com.ghostchu.quickshop.obj.QUserImpl;
import com.ghostchu.quickshop.util.MsgUtil;
import com.ghostchu.quickshop.util.Util;
import com.ghostchu.quickshop.util.economyformatter.EconomyFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaleNotificationTest {

  @BeforeAll
  static void initializeShopKeys() throws Exception {

    final Plugin plugin = mock(Plugin.class);
    when(plugin.getName()).thenReturn("QuickShop");
    try(final MockedStatic<QuickShopAPI> api = mockStatic(QuickShopAPI.class)) {
      api.when(QuickShopAPI::getPluginInstance).thenReturn(plugin);
      Class.forName(Shop.class.getName());
    }
  }


  @ParameterizedTest
  @CsvSource({"1, 12.5, 0", "64, 675.25, 67.525"})
  void saleNotificationUsesSettledGrossAmountAndTransactionCurrency(final int quantity, final double gross, final double tax) throws Exception {

    final QuickShop plugin = mock(QuickShop.class, RETURNS_DEEP_STUBS);
    final SimpleShopManager manager = mock(SimpleShopManager.class);
    final EconomyFormatter formatter = mock(EconomyFormatter.class);
    setField(manager, "plugin", plugin);
    setField(manager, "formatter", formatter);
    final Player player = mock(Player.class);
    final QUser seller = mock(QUser.class);
    final QUser owner = mock(QUser.class);
    final Shop shop = mock(Shop.class);
    final InventoryWrapper inventory = mock(InventoryWrapper.class);
    final AbstractEconomy economy = mock(AbstractEconomy.class);
    final Info info = mock(Info.class);
    final World world = mock(World.class);
    final ItemStack item = new ItemStack(Material.STONE);
    final TextManager texts = mock(TextManager.class);
    final Text text = mock(Text.class);
    final Component message = Component.text("settled sale");
    final String price = "credits " + gross;
    final SimpleEconomyTransaction transaction = mock(SimpleEconomyTransaction.class);
    final SimpleEconomyTransaction.SimpleEconomyTransactionBuilder builder = mock(SimpleEconomyTransaction.SimpleEconomyTransactionBuilder.class, RETURNS_SELF);
    when(builder.build()).thenReturn(transaction);
    when(transaction.checkBalance()).thenReturn(true);
    when(transaction.failSafeCommit()).thenReturn(true);
    when(transaction.getAmount()).thenReturn(gross);
    when(transaction.getTax()).thenReturn(tax);
    when(transaction.getWorld()).thenReturn(world);
    when(transaction.getCurrency()).thenReturn("settled-currency");
    when(formatter.format(gross, world, "settled-currency")).thenReturn(price);
    when(shop.getOwner()).thenReturn(owner);
    when(shop.getItem()).thenReturn(item);
    when(shop.getLocation()).thenReturn(new Location(world, 0, 0, 0));
    when(shop.getCurrency()).thenReturn("original-shop-currency");
    when(shop.getPrice()).thenReturn(99.0);
    when(shop.getRemainingSpace()).thenReturn(quantity + 1);
    when(seller.getDisplay()).thenReturn("Seller");
    when(plugin.perm().hasPermission(player, "quickshop.other.use")).thenReturn(true);
    when(plugin.text()).thenReturn(texts);
    when(texts.findRelativeLanguages(seller, true)).thenReturn(new ProxiedLocale("en-US", "en-US", NumberFormat.getInstance(Locale.US), Locale.US));
    when(texts.of(anyString(), any(Object[].class))).thenReturn(text);
    when(text.forLocale("en-US")).thenReturn(message);
    when(plugin.getPlatform().setItemStackHoverEvent(message, item)).thenReturn(message);
    doCallRealMethod().when(manager).actionBuying(player, inventory, economy, info, shop, quantity);

    try(final MockedStatic<QuickShop> quickShop = mockStatic(QuickShop.class);
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final MockedStatic<QUserImpl> users = mockStatic(QUserImpl.class);
        final MockedStatic<Util> util = mockStatic(Util.class);
        final MockedStatic<MsgUtil> messages = mockStatic(MsgUtil.class);
        final MockedStatic<SimpleEconomyTransaction> transactions = mockStatic(SimpleEconomyTransaction.class);
        final MockedConstruction<ShopSuccessPurchaseEvent> success = mockConstruction(ShopSuccessPurchaseEvent.class)) {
      quickShop.when(QuickShop::getInstance).thenReturn(plugin);
      users.when(()->QUserImpl.createFullFilled(player)).thenReturn(seller);
      util.when(()->Util.countItems(inventory, shop)).thenReturn(quantity);
      util.when(()->Util.getItemStackName(item)).thenReturn(Component.text("Stone"));
      util.when(()->Util.asyncThreadRun(any(Runnable.class))).thenAnswer(call->{
        call.<Runnable>getArgument(0).run();
        return null;
      });
      transactions.when(SimpleEconomyTransaction::builder).thenReturn(builder);
      assertTrue(manager.actionBuying(player, inventory, economy, info, shop, quantity));
      verify(texts).of("player-sold-to-your-store", "Seller", quantity, Component.text("Stone"), price);
      verify(formatter).format(gross, world, "settled-currency");
      messages.verify(()->MsgUtil.send(shop, owner, message));
    }
  }

  @Test
  void allLocalesRenderAndSurviveOfflineJsonRoundTrip() throws Exception {

    try(final MockedStatic<MsgUtil> messages = mockStatic(MsgUtil.class, CALLS_REAL_METHODS);
        final var locales = Files.list(Path.of("../crowdin/lang"))) {
      final List<Path> templates = new ArrayList<>(locales.map(path->path.resolve("messages.yml")).toList());
      templates.add(Path.of("src/main/resources/lang/messages.yml"));
      for(final Path path : templates) {
        final String template = YamlConfiguration.loadConfiguration(path.toFile()).getString("player-sold-to-your-store");
        final Component rendered = MsgUtil.fillArgs(MiniMessage.miniMessage().deserialize(template), Component.text("Seller"), Component.text("64"), Component.text("Stone"), Component.text("credits 675.25"));
        final String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
        assertFalse(plain.matches(".*\\{[0-9]+}.*"), path.toString());
        if(template.contains("{3}")) {
          assertTrue(plain.contains("credits 675.25"), path.toString());
        }
        final String stored = GsonComponentSerializer.gson().serialize(rendered);
        assertEquals(rendered, GsonComponentSerializer.gson().deserialize(stored), path.toString());
      }
    }
  }

  private static void setField(final Object instance, final String name, final Object value) throws Exception {

    final Field field = AbstractShopManager.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(instance, value);
  }
}
