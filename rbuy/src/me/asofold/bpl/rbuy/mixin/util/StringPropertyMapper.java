package me.asofold.bpl.rbuy.mixin.util;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Map string properties to a normalized version which can be compared with some set.
 * TODO: compiled / efficient implementation ? (is future, because this is applied to configuration only and on checking other plugins)
 * 
 * @author mc_dev
 *
 */
public class StringPropertyMapper {
	
	/**
	 * Check if String matches another String in the collection. Give '*' to indicate if the preset is a suffix or prefix.
	 * This uses case-sensitive comparison. 
	 * This does not use any efficient technique.
	 * @param presets A collection of strings to compare against, if  starting with '*' regarded as a suffix, if ending with '*' regarded as a prefix.
	 * @param cmpValue This is compared against the presets, no '*' is processed here
	 * @return String The matching element from presets or null. 
	 */
	public static String matchingString(Collection<String> presets, String cmpValue){
		if (presets.contains(cmpValue)) return cmpValue;
		String out = null;
		 
//		Map<String,String> lowerCaseMap = new HashMap<String, String>();
//		for ( String x : presets){
//			lowerCaseMap.put(x.toLowerCase(), x);
//		}
//		String lcValue = cmpValue.toLowerCase();
//		if (lowerCaseMap.containsKey(lcValue)) return lowerCaseMap.get(lcValue);
		
		List<String> suffixes = new LinkedList<String>();
		for (String x : presets){
			if ( x.startsWith("*")) suffixes.add(x.substring(1));
		}
		
		for (String x : suffixes){
			if (cmpValue.endsWith(x)) return x;
		}
		
		List<String> prefixes = new LinkedList<String>();
		for ( String x : presets){
			if (x.endsWith("*")) prefixes.add(x.substring(0,x.length()-1));
		}
		
		for (String x : prefixes){
			if (cmpValue.startsWith(x)) return x;
		}
		
		
		return out;
	}
}
