package asofold.rbuy.compatlayer;


import java.util.List;

/**
 * CONVENTIONS: 
 * - Return strings if objects can be made strings.
 * - No exceptions, rather leave elements out of lists.
 * - Lists of length 0 and null can not always be distinguished (?->extra safe wrapper ?)
 * - All contents are treated alike, even if given as a string (!): true and 'true', 1 and '1'
 * @author mc_dev
 *
 */
public interface CompatConfig {
	
	public boolean hasEntry(String path);

	public void load();
	
	public boolean save();

	public Double getDouble(String path, Double defaultValue);

	public Long getLong(String path, Long defaultValue);

	public String getString(String path, String defaultValue);

	public Integer getInt(String path, Integer defaultValue);
	
	public List<String> getStringKeys(String path);
	
	public List<Object> getKeys(String path);
	public List<Object> getKeys();

	public Object getProperty(String path, Object defaultValue);

	public List<String> getStringKeys();

	public void setProperty(String path, Object obj);

	public List<String> getStringList(String path, List<String> defaultValue);
	
	/**
	 * Only accepts true and false , 'true' and 'false'.
	 * @param path
	 * @param defaultValue
	 * @return
	 */
	public Boolean getBoolean(String path, Boolean defaultValue);
	
	public List<Integer> getIntList(String path, List<Integer> defaultValue);
	
	public void removeProperty(String path);
	
	public List<Double> getDoubleList(String path , List<Double> defaultValue);
	
}
