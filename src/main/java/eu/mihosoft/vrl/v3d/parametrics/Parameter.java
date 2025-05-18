package eu.mihosoft.vrl.v3d.parametrics;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

public class Parameter {
	
	private String name=null;
	private final ArrayList<String> options=new ArrayList<String>();
	private Long value=null;
	private String strValue=null;
	CSGDatabase csgDatabase=null;
	public Parameter(CSGDatabase db){
		csgDatabase=db;
	}
	
	protected void setup(String key,Long defaultValue,ArrayList<String> options){
		this.name = key;
		if(csgDatabase.get(name)==null)
			setValue(defaultValue);
		else{
			setValue(csgDatabase.get(name).getValue());
		}
		csgDatabase.addParameterListener(name, new IParameterChanged() {
			@Override
			public void parameterChanged(String name, Parameter p) {
				value = p.getValue();// if another instance of parameter with this key changes value
			}
		});
		for(String o:options){
			this.options.add(o);
		}
		csgDatabase.set(key, this);
	}
	protected void setup(String key,String defaultValue,ArrayList<String> options){
		this.name = key;
		if(csgDatabase.get(name)==null)
			this.strValue = defaultValue;
		else{
			this.strValue = csgDatabase.get(name).getStrValue();
		}
		csgDatabase.addParameterListener(name, new IParameterChanged() {
			@Override
			public void parameterChanged(String name, Parameter p) {
				strValue = p.getStrValue();// if another instance of parameter with this key changes value
			}
		});
		for(String o:options){
			this.options.add(o);
		}
		csgDatabase.set(key, this);
	}
	public String getName() {
		return name;
	}
	
	public void setValue(Long newVal){
		if(value!=newVal){
			value=newVal;
			CopyOnWriteArrayList<IParameterChanged> listeners = csgDatabase.getParamListeners(name);
			for(int i=0;i<listeners.size();i++){
			  IParameterChanged l=listeners.get(i);
				l.parameterChanged(name, this);
			}
		}
	}
	
	public Long getValue() {
		return value;
	}
	public ArrayList<String> getOptions() {
		return options;
	}

	public String getStrValue() {
		return strValue;
	}

	public void setStrValue(String newValue) {
		if(!strValue.contentEquals(newValue)){
			strValue = newValue;
			CopyOnWriteArrayList<IParameterChanged> listeners = csgDatabase.getParamListeners(name);
			for(IParameterChanged l:listeners){
				l.parameterChanged(name, this);
			}
		}
		
	}
	

	public void setMM(double newVal){
		setValue(new Long((long)(newVal*1000.0)));
	}
	public void setMicrons(long newVal){
		setValue(new Long(newVal));
	}
	
	public double getMM(){
		return (Double.parseDouble(getValue().toString()))/1000.0;
	}
	public double getMicrons(){
		return (Long)getValue();
	}
	

}
