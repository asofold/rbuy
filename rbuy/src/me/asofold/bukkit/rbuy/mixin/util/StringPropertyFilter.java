package me.asofold.bukkit.rbuy.mixin.util;

import java.util.Collection;
import java.util.LinkedList;

/**
 * For need-not-be-fast plugin/interface filtering (include,exclude).<br>
 * If include is set, only names matching include will be considered.<br>
 * If name matches exclude it will not pass.
 *
 * @author mc_dev
 *
 */
public class StringPropertyFilter {
	public Collection<String> include = new LinkedList<String>();
	public Collection<String> exclude= new LinkedList<String>();
	
	public StringPropertyFilter(Collection<String> include, Collection<String> exclude){
		if (include != null){
			this.include.addAll(include);
		}
		if (exclude != null){
			this.exclude.addAll(exclude);
		}
	}
	
	public boolean passesFilter( String prop ){
		if ( !this.include.isEmpty()){
			if ( StringPropertyMapper.matchingString(include, prop)==null ) return false;
		}
		
		if ( !this.exclude.isEmpty()){
			if ( StringPropertyMapper.matchingString(exclude, prop)!=null) return false;
		}
		
		return true;
	}
}
