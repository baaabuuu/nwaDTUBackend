package dtu.alarmSystemBackend;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.thethingsnetwork.data.common.Connection;
import org.thethingsnetwork.data.mqtt.Client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;

import org.thethingsnetwork.data.common.messages.ActivationMessage;
import org.thethingsnetwork.data.common.messages.DataMessage;
import org.thethingsnetwork.data.common.messages.DownlinkMessage;

import dtu.components.Component;
import dtu.database.Database;
import dtu.house.House;
import dtu.house.PhoneAddress;
import dtu.ttnCommunication.MSGrecver;

/**
 * Hello world!
 *
 */
public class Main 
{
	private Database<House> houseDB;
	private Database<Component> deviceDB;
	private Database<PhoneAddress> phoneAddrDB;
	private ArrayList<House> warningHouses = new ArrayList<House>();
	private Alarm alarm;
	
	
	private Gson gson;
	private TimerSystem timerSystem;
	private Client client;
	
	
	public static void main(String[] args) throws MqttException, Exception
	{
		new Main();

	}
		
	public Main() throws MqttException, Exception
	{
		gson = new GsonBuilder().create();
		try {
			readDatabaseFiles();
		} catch (IOException e)
		{
			System.out.println("Failed to setup database files - program failed");
			e.printStackTrace();
			return;
		}
		timerSystem = new TimerSystem();
		timerSystem.init(alarm, warningHouses);
		alarm = new Alarm(phoneAddrDB);
		try {
			clientSetup();
		} catch (URISyntaxException e) {
			e.printStackTrace();
			System.out.println("Failed to setup communication - program failed");
			return;
		}
		while(true)
		{
			System.out.print(".");
			Thread.sleep(1000);		
		}
		
	}
	
	
	/**
	 * Reads the databasefiles HouseDB.json, DeviceDB.json and PhoneAddrDB.json.
	 * @throws IOException If the files are unavailable it throws an error.
	 */
	public void readDatabaseFiles() throws IOException
	{
	    Gson gson = new GsonBuilder().create();
	    
	    JsonReader reader = new JsonReader(new FileReader("HouseDB.json"));
	    houseDB = gson.fromJson(reader, Database.class); 
	    reader.close();
	    
	    reader = new JsonReader(new FileReader("DeviceDB.json"));
	    deviceDB = gson.fromJson(reader, Database.class); 
	    reader.close();

	    reader = new JsonReader(new FileReader("PhoneAddrDB.json"));
	    phoneAddrDB = gson.fromJson(reader, Database.class);
	    reader.close();
	}
	
	public void clientSetup() throws MqttException, Exception
	{
		client = new MSGrecver().setupRecver();

        client.onActivation((String _devId, ActivationMessage _data) -> System.out.println("Activation: " + _devId + ", data: " + _data.getDevAddr()));

        client.onError((Throwable _error) -> System.err.println("error: " + _error.getMessage()));

        client.onConnected((Connection _client) -> System.out.println("connected !"));


		//Upon getting a message, this is how its handled - handle -> converted to byte stream -> put into its container -> sent
		client.onMessage(null, (String devId, DataMessage data) -> {
			System.out.println("here");
			Optional<JsonElement> result = handleMessage(data, devId);
			System.out.println("here");
			JsonObject elem = new JsonObject();
			byte[] output = null;
			try {
				output = convertToBytes(elem);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			System.out.print("\nsending: the following ");
			for (byte msg : output)
			{
				System.out.print(msg);
			}
			System.out.println("");
    		DownlinkMessage response = new DownlinkMessage(1, output);
    		try {
				client.send(devId, response);
			} catch (Exception e) {
				System.out.println("Failed to send response back to " + devId);
				e.printStackTrace();
			}
		});
		
		client.start();
	}
	
	private Optional<JsonElement> handleMessage(DataMessage data, String deviceID) {
		JsonObject output = new JsonObject();
		JsonObject input = gson.toJsonTree(data).getAsJsonObject();
		
		System.out.println("Is there anything?");

        Predicate<Component> filterFunction = n -> n.getComponentID().getID().equals(deviceID);
		System.out.println("HELLO");
		System.out.println(deviceDB == null);
        Optional<Component> optDevice = deviceDB.get(filterFunction);
		System.out.println("WORLD");

        if (!optDevice.isPresent())
        {
        	//If component does not exist.
        	return Optional.empty();
        }
		System.out.println("Is there anything?");

        Component component = optDevice.get();
        component.updateLastDate(new Date());
        
        Predicate<House> filterFunctionHouse = n -> n.getHouseID().getID().equals(component.getHouseID().getID());
        
        Optional<House> optHouse = houseDB.get(filterFunctionHouse);
        if (!optHouse.isPresent()) //figure out how to handle this - component exists but house got deleted? Wrong house ID perhaps in DB? Indicates corruption
        {
        	return Optional.empty();
        }
       
       House house = optHouse.get();
       //Check if conflict
       boolean panicRecv = input.get("panic").getAsBoolean();
       boolean statusRecv = input.get("status").getAsBoolean();
       String pwRecv = input.get("password").getAsString();
       boolean pwCheck = false;
       component.updateLastDate(new Date());
       if (pwRecv.length() > 0)
       { // password - toggle things
    	 // needs some form of verification
    	   timerSystem.lockWarningHouses();
    	   house.toggleArm();
           pwCheck = true;
    	   timerSystem.unlockWarningHouse();

       }
       else if (statusRecv && house.getArmStatus()) 
       { //ALARM START
    	   timerSystem.lockWarningHouses();
		   if (!warningHouses.contains(house))
		   {
			   warningHouses.add(house);
			   house.setHouseTime(60);
		   }
		   
		   timerSystem.unlockWarningHouse();

       }
       else if (statusRecv && panicRecv)
       {
    	   alarm.alarm(house);
       }
       output.addProperty("armStatus", house.getArmStatus());
       output.addProperty("panic",  false);
       output.addProperty("status", pwCheck);
       return Optional.of(output);
	}

	private byte[] convertToBytes(Object object) throws IOException
	{
	    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
	         ObjectOutput out = new ObjectOutputStream(bos))
	    {
	        out.writeObject(object);
	        return bos.toByteArray();
	    } catch (IOException e)
	    {
	    	System.out.println("convertToBytes Failed - another thread is accessing the same object.");
			e.printStackTrace();
		}
		return null;
	}
}
