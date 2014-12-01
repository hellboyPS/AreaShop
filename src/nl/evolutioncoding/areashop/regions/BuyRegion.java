package nl.evolutioncoding.areashop.regions;

import java.util.Calendar;
import java.util.HashMap;
import java.util.UUID;

import net.milkbowl.vault.economy.EconomyResponse;
import nl.evolutioncoding.areashop.AreaShop;
import nl.evolutioncoding.areashop.exceptions.RegionCreateException;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class BuyRegion extends GeneralRegion {

	public BuyRegion(AreaShop plugin, YamlConfiguration config) throws RegionCreateException {
		super(plugin, config);
	}
	
	public BuyRegion(AreaShop plugin, String name, World world) {
		super(plugin, name, world);
	}
	
	@Override
	public RegionType getType() {
		return RegionType.BUY;
	}
	
	@Override
	public RegionState getState() {
		if(isSold() && isInResellingMode()) {
			return RegionState.RESELL;
		} else if(isSold() && !isInResellingMode()) {
			return RegionState.SOLD;
		} else {
			return RegionState.FORSALE;
		}
	}
	
	/**
	 * Get the UUID of the owner of this region
	 * @return The UUID of the owner of this region
	 */
	public UUID getBuyer() {
		String buyer = getStringSetting("buy.buyer");
		if(buyer != null) {
			try {
				return UUID.fromString(buyer);
			} catch(IllegalArgumentException e) {}
		}
		return null;
	}
	
	/**
	 * Check if a player is the buyer of this region
	 * @param player Player to check
	 * @return true if this player owns this region, otherwise false
	 */
	public boolean isBuyer(Player player) {
		if(player == null) {
			return false;
		} else {
			return isBuyer(player.getUniqueId());
		}
	}
	public boolean isBuyer(UUID player) {
		UUID buyer = getBuyer();
		if(buyer == null || player == null) {
			return false;
		} else {
			return buyer.equals(player);
		}
	}
	
	/**
	 * Set the buyer of this region
	 * @param buyer The UUID of the player that should be set as buyer
	 */
	public void setBuyer(UUID buyer) {
		if(buyer == null) {
			setSetting("buy.buyer", null);
			setSetting("buy.buyerName", null);
		} else {
			setSetting("buy.buyer", buyer.toString());
			setSetting("buy.buyerName", plugin.toName(buyer));
		}
	}
	
	/**
	 * Get the name of the player that owns this region
	 * @return The name of the player that owns this region
	 */
	public String getPlayerName() {
		return plugin.toName(getBuyer());
	}
	
	/**
	 * Check if the region is sold
	 * @return true if the region is sold, otherwise false
	 */
	public boolean isSold() {
		return getBuyer() != null;
	}
	
	/**
	 * Check if the region is being resold
	 * @return true if the region is available for reselling, otherwise false
	 */
	public boolean isInResellingMode() {
		return config.getBoolean("buy.resellMode");
	}
	
	/**
	 * Get the price of the region
	 * @return The price of the region
	 */
	public double getPrice() {
		return getDoubleSetting("buy.price");
	}
	
	/**
	 * Get the resell price of this region
	 * @return The resell price if isInResellingMode(), otherwise 0.0
	 */
	public double getResellPrice() {
		return config.getDouble("buy.resellPrice");
	}
	
	/**
	 * Get the formatted string of the price (includes prefix and suffix)
	 * @return The formatted string of the price
	 */
	public String getFormattedPrice() {
		return plugin.formatCurrency(getPrice());
	}
	
	/**
	 * Get the formatted string of the resellprice (includes prefix and suffix)
	 * @return The formatted string of the resellprice
	 */
	public String getFormattedResellPrice() {
		return plugin.formatCurrency(getResellPrice());
	}
	
	/**
	 * Change the price of the region
	 * @param price
	 */
	public void setPrice(double price) {
		setSetting("buy.price", price);
	}
	
	/**
	 * Set the region into resell mode with the given price
	 * @param price The price this region should be put up for sale
	 */
	public void enableReselling(double price) {
		config.set("buy.resellMode", true);
		config.set("buy.resellPrice", price);
	}
	
	/**
	 * Stop this region from being in resell mode
	 */
	public void disableReselling() {
		config.set("buy.resellMode", null);
		config.set("buy.resellPrice", null);
	}
	
	@Override
	public HashMap<String, Object> getSpecificReplacements() {
		// Fill the replacements map with things specific to a BuyRegion
		HashMap<String, Object> result = new HashMap<String, Object>();
		result.put(AreaShop.tagPrice, getFormattedPrice());
		result.put(AreaShop.tagPlayerName, getPlayerName());
		result.put(AreaShop.tagPlayerUUID, getBuyer());
		result.put(AreaShop.tagResellPrice, getFormattedResellPrice());
		// TODO: Add more?
		
		return result;
	}
	
	/**
	 * Buy a region
	 * @param player The player that wants to buy the region
	 * @return true if it succeeded and false if not
	 */
	public boolean buy(Player player) {
		/* Check if the player has permission */
		if(player.hasPermission("areashop.buy")) {	
			if(!isSold() || (isInResellingMode() && !isBuyer(player))) {
				boolean isResell = isInResellingMode();
				// Check if the players needs to be in the world or region for buying
				if(!player.getWorld().getName().equals(getWorldName()) && getBooleanSetting("general.restrictedToWorld")) {
					plugin.message(player, "buy-restrictedToWorld", getWorldName(), player.getWorld().getName());
					return false;
				}
				if((!player.getWorld().getName().equals(getWorldName()) 
						|| !getRegion().contains(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ())
					) && getBooleanSetting("general.restrictedToRegion")) {
					plugin.message(player, "buy-restrictedToRegion", getName());
					return false;
				}				
				// Check region limits
				LimitResult limitResult = this.limitsAllowBuying(player);
				AreaShop.debug("LimitResult: " + limitResult.toString());
				if(!limitResult.actionAllowed()) {
					if(limitResult.getLimitingFactor() == LimitType.TOTAL) {
						plugin.message(player, "total-maximum", limitResult.getMaximum(), limitResult.getCurrent(), limitResult.getLimitingGroup());
						return false;
					}
					if(limitResult.getLimitingFactor() == LimitType.BUYS) {
						plugin.message(player, "buy-maximum", limitResult.getMaximum(), limitResult.getCurrent(), limitResult.getLimitingGroup());
						return false;
					}
					// Should not be reached, but is safe like this
					return false;
				}
				
				/* Check if the player has enough money */
				if(plugin.getEconomy().has(player, getWorldName(), getPrice())) {
					UUID oldOwner = getBuyer();
					if(isResell && oldOwner != null) {
						double resellPrice = getResellPrice();
						/* Transfer the money to the previous owner */
						EconomyResponse r = plugin.getEconomy().withdrawPlayer(player, getWorldName(), getResellPrice());
						if(!r.transactionSuccess()) {
							plugin.message(player, "buy-payError");
							return false;
						}
						OfflinePlayer oldOwnerPlayer = Bukkit.getOfflinePlayer(oldOwner);
						if(oldOwnerPlayer != null) {
							r = plugin.getEconomy().depositPlayer(oldOwnerPlayer, getWorldName(), getResellPrice());
							if(!r.transactionSuccess()) {
								plugin.getLogger().info("Something went wrong with paying '" + oldOwnerPlayer.getName() + "' for his resell of region " + getName());
							}
						}
						// Resell is done, disable that now
						disableReselling();
						// Run commands
						this.runEventCommands(RegionEvent.RESELL, true);
						// Set the owner
						setBuyer(player.getUniqueId());
		
						// Update everything
						handleSchematicEvent(RegionEvent.RESELL);
						updateSigns();
						updateRegionFlags();

						// Send message to the player
						plugin.message(player, "buy-successResale", getName(), oldOwnerPlayer.getName());
						Player seller = Bukkit.getPlayer(oldOwner);
						if(seller != null) {
							plugin.message(player, "buy-successSeller", getName(), getPlayerName(), resellPrice);
						}						
						AreaShop.debug(player.getName() + " has bought region " + getName() + " for " + getFormattedPrice() + " from " + oldOwnerPlayer.getName());
		
						this.saveRequired();
						// Run commands
						this.runEventCommands(RegionEvent.RESELL, false);
					} else {
						// Substract the money from the players balance
						EconomyResponse r = plugin.getEconomy().withdrawPlayer(player, getWorldName(), getPrice());
						if(!r.transactionSuccess()) {
							plugin.message(player, "buy-payError");
							return false;
						}
						AreaShop.debug(player.getName() + " has bought region " + getName() + " for " + getFormattedPrice());
						
						// Run commands
						this.runEventCommands(RegionEvent.BOUGHT, true);
						// Set the owner
						setBuyer(player.getUniqueId());
		
						// Update everything
						handleSchematicEvent(RegionEvent.BOUGHT);
						updateSigns();
						updateRegionFlags();

						// Send message to the player
						plugin.message(player, "buy-succes", getName());
						this.saveRequired();
						// Run commands
						this.runEventCommands(RegionEvent.BOUGHT, false);
					}				
					return true;
				} else {
					/* Player has not enough money */
					plugin.message(player, "buy-lowMoney", plugin.formatCurrency(plugin.getEconomy().getBalance(player, getWorldName())), getFormattedPrice());
				}
			} else {
				if(isBuyer(player)) {
					plugin.message(player, "buy-yours");
				} else {
					plugin.message(player, "buy-someoneElse");
				}
			}	
		} else {
			plugin.message(player, "buy-noPermission");
		}
		return false;
	}
	
	/**
	 * Sell a buyed region, get part of the money back
	 * @param regionName
	 */
	public void sell(boolean giveMoneyBack) {
		// Run commands
		this.runEventCommands(RegionEvent.SOLD, true);
		
		disableReselling();
		/* Give part of the buying price back */
		double percentage = getDoubleSetting("buy.moneyBack") / 100.0;
		double moneyBack =  getPrice() * percentage;
		if(moneyBack > 0 && giveMoneyBack) {
			/* Give back the money */
			OfflinePlayer player = Bukkit.getOfflinePlayer(getBuyer());
			if(player != null) {
				EconomyResponse response = null;
				boolean error = false;
				try {
					response = plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(getBuyer()), getWorldName(), moneyBack);
				} catch(Exception e) {
					error = true;
				}
				if(error || response == null || !response.transactionSuccess()) {
					plugin.getLogger().info("Something went wrong with paying back money to " + getPlayerName() + " while selling region " + getName());
				}	
			}
		}
		
		/* Debug message */
		AreaShop.debug(getPlayerName() + " has sold " + getName() + ", got " + plugin.formatCurrency(moneyBack) + " money back");

		/* Update everything */
		handleSchematicEvent(RegionEvent.SOLD);
		updateRegionFlags(RegionState.FORSALE);
		
		/* Remove friends and the owner */
		clearFriends();
		setBuyer(null);		
		
		updateSigns();
		
		this.saveRequired();
		// Run commands
		this.runEventCommands(RegionEvent.SOLD, false);
	}

	@Override
	public boolean checkInactive() {
		if(!isSold()) {
			return false;
		}
		OfflinePlayer player = Bukkit.getOfflinePlayer(getBuyer());
		//AreaShop.debug("inactive checking for " + getName() + ", player=" + player.getName() + ", currenttime=" + Calendar.getInstance().getTimeInMillis() + ", lastPlayed=" + player.getLastPlayed() + ", diff=" + (Calendar.getInstance().getTimeInMillis() - player.getLastPlayed()));
		int inactiveSetting = getIntegerSetting("buy.inactiveTimeUntilSell");
		if(inactiveSetting <= 0 || player.isOp()) {
			return false;
		}
		if(Calendar.getInstance().getTimeInMillis() > (player.getLastPlayed() + inactiveSetting * 60 * 1000)) {
			plugin.getLogger().info("Region " + getName() + " sold because of inactivity for player " + getPlayerName());
			this.sell(true);
			return true;
		}
		return false;
	}

}

























