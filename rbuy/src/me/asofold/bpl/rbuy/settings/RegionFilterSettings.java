package me.asofold.bpl.rbuy.settings;

import me.asofold.bpl.rbuy.settings.compatlayer.CompatConfig;

/**
 * Settings that can be done by a world/region filter, as well as on top level.
 * @author mc_dev
 *
 */
public class RegionFilterSettings {
	
	// Allow setting other values here.
	public Integer maxBuy = null;
	public Integer timeCountBuy = null;
	
	public static RegionFilterSettings fromConfig(CompatConfig cfg, String prefix) {
		RegionFilterSettings rfs = new RegionFilterSettings();
		rfs.maxBuy = cfg.getInt(prefix + "max-buy", null);
		rfs.timeCountBuy = cfg.getInt(prefix + "time-count-buy", null);
		if (!rfs.isEmpty()) {
			return rfs;
		} else {
			return null;
		}
	}
	
	public void toConfig(CompatConfig cfg, String prefix) {
		if (maxBuy != null) {
			cfg.set(prefix + "max-buy", maxBuy);
		}
		if (timeCountBuy != null) {
			cfg.set(prefix + "time-count-buy", timeCountBuy);
		}
	}

	public boolean isEmpty() {
		return maxBuy == null && timeCountBuy == null;
	}
}
