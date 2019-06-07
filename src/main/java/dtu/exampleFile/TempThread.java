package dtu.exampleFile;

import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import dtu.alarmSystemBackend.Setup;
import dtu.components.Component;
import dtu.components.ComponentSignal;
import dtu.database.Database;
import dtu.database.DatabaseArrayList;
import dtu.house.House;
import dtu.house.HouseID;
import dtu.house.HouseIDValue;
import dtu.house.HouseImplementation;
import dtu.house.PhoneAddress;
import dtu.house.PhoneAddressImplementation;

public class TempThread {
	
	public static void main(String[] args) throws InterruptedException, IOException
	{
		Database<PhoneAddress> phoneAddrDB = new DatabaseArrayList<PhoneAddress>();
		Database<House> houseDB = new DatabaseArrayList<House>();

		Database<Component> deviceDB = new DatabaseArrayList<Component>();


		Setup setup = new Setup();
		
		System.out.println("hello");
		readDatabaseFiles();
		List<Component> elems = deviceDB.filter(device -> device.equals(device));
		System.out.println(elems.size());
		byte[] salt = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x00, 0x00};		
		System.out.println(salt.length);

		
		HouseID houseID = new HouseIDValue("HouseID");
		
		House house = new HouseImplementation("temp addr", houseID, "1234", salt);
	    Component device1 = new ComponentSignal("device_01", houseID);
	    Component device2 = new ComponentSignal("device_02", houseID);
	    
	    PhoneAddress phoneNumber = new PhoneAddressImplementation("22932970", houseID);
		
		
		houseDB.add(house);
		deviceDB.add(device1);
		deviceDB.add(device2);
		phoneAddrDB.add(phoneNumber);
		elems = deviceDB.filter(device -> device.equals(device));
		System.out.println(elems.size());

		
		FileOutputStream f1;
		try {
			System.out.println("are we writing");
			f1 = new FileOutputStream(new File("database_house.txt"));
			ObjectOutputStream  o1 = new ObjectOutputStream (f1);
			FileOutputStream f2 = new FileOutputStream(new File("database_phone.txt"));
			ObjectOutputStream  o2 = new ObjectOutputStream (f2);
			FileOutputStream f3 = new FileOutputStream(new File("database_component.txt"));
			ObjectOutputStream  o3 = new ObjectOutputStream (f3);
			
			o1.writeObject(houseDB);
			o2.writeObject(phoneAddrDB);
			o3.writeObject(deviceDB);
			

			
			o1.close();
			f1.close();
			o2.close();
			f2.close();
			o3.close();
			f3.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	public static void readDatabaseFiles() throws IOException
	{	    
		FileInputStream fileInput1 = new FileInputStream(new File("database_house.txt"));
		ObjectInputStream inputStream1 = new ObjectInputStream(fileInput1);
		FileInputStream fileInput2 = new FileInputStream(new File("database_phone.txt"));
		ObjectInputStream inputStream2 = new ObjectInputStream(fileInput2);
		FileInputStream fileInput3 = new FileInputStream(new File("database_component.txt"));
		ObjectInputStream inputStream3 = new ObjectInputStream(fileInput3);

		// Read objects
		try {
			DatabaseArrayList<House> houseDB = (DatabaseArrayList<House>) inputStream1.readObject();
			DatabaseArrayList<Component> deviceDB = (DatabaseArrayList<Component>) inputStream3.readObject();

		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		fileInput1.close();
		inputStream1.close();
		fileInput2.close();
		inputStream2.close();
		fileInput3.close();
		inputStream3.close();
	}

}