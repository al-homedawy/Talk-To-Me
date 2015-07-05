package com.example.bluetoothchat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.R.string;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.*;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.os.Build;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;


public class MainActivity extends ActionBarActivity {
	
	public static BluetoothManager bluetooth;
	private BluetoothServerManager server;	
	private BluetoothClientManager client;
	private InitializeChat chat;
	private ConnectionManager chatManager;

	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main);
		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
	}
	
	@Override
	protected void onDestroy ()
	{		
		// Unregister receiver
		bluetooth.UnregisterReceiver ();
	}
	
	@Override
	protected void onActivityResult ( int request, int result, Intent data )
	{
		// Requesting the user to enable bluetooth
		if ( request == BluetoothManager.ENABLE_BLUETOOTH )
		{
			if ( result != RESULT_OK )
				// The user rejected Bluetooth to start
				bluetooth.CancelAdapter ();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	public static void MessageBox ( String Message, Context context )
	{
		AlertDialog.Builder msg = new AlertDialog.Builder ( context );
		msg.setMessage(Message);
		msg.setTitle("Message");
		msg.setPositiveButton("OK", null);
		msg.show ();
	}
	
	private void LaunchChat ()
	{		
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		
		transaction.replace(R.id.container, new ChatholderFragment() );
		transaction.addToBackStack(null);
		transaction.commit ();
	}
	
	private class ConnectionManager extends Thread
	{
		private BluetoothSocket m_Partner;
		private InputStream m_InputStream;
		private OutputStream m_OutputStream;
		private View m_RootView;
		
		public ConnectionManager ( BluetoothSocket partner, View rootView )
		{
			m_RootView = rootView;
			m_Partner = partner;
			m_InputStream = null;
			m_OutputStream = null;
			
			try
			{
				m_InputStream = m_Partner.getInputStream ();
				m_OutputStream = m_Partner.getOutputStream ();
			}
			catch ( Exception ex )
			{
				// Something went wrong
			}
		}
		
		@Override
		public void run ()
		{
			byte[] buffer = new byte[1024];  
	        int bytes; 
	 
	        // Keep listening to the InputStream until an exception occurs
	        while (true) {
	            try {
	                bytes = m_InputStream.read(buffer);
	                final byte[] concat = Arrays.copyOf ( buffer, bytes );
	                
	                m_RootView.post ( new Runnable () {
	                	public void run ()
	                	{	                		
	                		bluetooth.AddMessage( "Partner: " + new String(concat) );
	                	}	                
	                });
	                
	                Log.d ("%s", bluetooth.GetPartner().getRemoteDevice().getAddress());
	                Log.d ( "%s", "Partner: " + new String(concat) );
	                
	            } catch (Exception e) {
	            	Log.d ("%s", "Error 1" );
	            	Log.d ("%s", e.getMessage());
	                break;
	            }
	        }
		}
		
		public void write(byte[] bytes) {
	        try {
	            m_OutputStream.write(bytes);
	        } catch (Exception e) { }
	    }
		
		public void cancel() {
	        try {
	            m_Partner.close();
	        } catch (Exception e) { }
	    }
	}
	
	private class BluetoothClientManager extends Thread
	{
		private BluetoothAdapter m_Adapter;
		private BluetoothDevice m_Device;
		private BluetoothSocket m_Socket;
		private UUID m_ID;
		private Context m_Context;
		
		public BluetoothClientManager ( BluetoothDevice Device, BluetoothAdapter Adapter, Context context )
		{
			m_Adapter = Adapter;
			m_Device = Device;
			m_Socket = null;
			m_Context = context;
			m_ID = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d"); //m_Device.getUuids() [0].getUuid ();
		}
		
		@Override
		public void run ()
		{
			m_Adapter.cancelDiscovery();
			
			try
			{
				m_Socket = m_Device.createRfcommSocketToServiceRecord(m_ID);
			}
			catch ( Exception ex )
			{				
				Log.d ("%s", "Error 2" );
				Log.d ("%s", ex.getMessage());
				
				// Something went wrong
				return;
			}
			
			try
			{
				m_Socket.connect ();
				bluetooth.SetPartner( m_Socket );
				server.cancel ();
			}
			catch ( Exception connectEx )
			{
				Log.d ("%s", "Error 3" );
				Log.d ("%s", connectEx.getMessage());
				
				try {
					m_Socket.close ();	
				}
				catch ( Exception closeEx ) {
					// Something went wrong
					Log.d ("%s", "Error 4" );
					Log.d ("%s", closeEx.getMessage());
				}
				
				return;
			}
		}
		
		public void cancel ()
		{
			try
			{
				m_Socket.close ();
			}
			catch ( Exception ex )
			{
				Log.d ("%s", "Error 5" );
				Log.d ("%s", ex.getMessage());
				// Something went wrong
			}
		}
	}
	
	private class BluetoothServerManager extends Thread
	{
		private BluetoothServerSocket m_ServerSocket;
		private BluetoothAdapter m_Adapter;
		private BluetoothDevice m_Device;
		private BluetoothSocket m_Socket;
		private Context m_Context;
		
		public BluetoothServerManager ( BluetoothAdapter Adapter, String MacAddress, Context context )
		{			
			m_ServerSocket = null;
			m_Adapter = Adapter;
			m_Device = m_Adapter.getRemoteDevice(MacAddress);
			m_Socket = null;
			m_Context = context;
		}
		
		/*
		 * Host/connect to a server with the target device UUID.
		*/
		@Override
		public void run ()
		{		
			// Setup the server socket
			try
			{
				m_ServerSocket = m_Adapter.
						listenUsingRfcommWithServiceRecord("BluetoothChat", UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d") );//m_Device.getUuids() [0].getUuid());
			}
			catch ( Exception ex )
			{				
				Log.d ("%s", "Error 6" );
				Log.d ("%s", ex.getMessage());
				
				// Something went wrong
				return;
			}
			
			while ( true )
			{				
				try
				{
					m_Socket = m_ServerSocket.accept ();
				}
				catch ( Exception ex )
				{					
					Log.d ("%s", "Error 7" );
					Log.d ("%s", ex.getMessage());
					
					// Something went wrong
					break;
				}
				
				if ( m_Socket != null )
				{					
					try {
						bluetooth.SetPartner( m_Socket );
						m_ServerSocket.close ();
						client.cancel ();
					} catch (Exception ex) 
					{		
						Log.d ("%s", "Error 8" );
						Log.d ("%s", ex.getMessage());
						
						// Something went wrong
						break;
					}

					// Still here?
					break;
				}
			}
		}
		
		
		public void cancel ()
		{
			try {
				m_ServerSocket.close ();
			}
			catch ( Exception ex )
			{
				Log.d ("%s", "Error 9" );
				Log.d ("%s", ex.getMessage());
				
				// Something went wrong
			}
		}
	}
	
	private class InitializeChat extends Thread
	{
		private Context m_Context;
		
		public InitializeChat ( Context context)
		{
			m_Context = context;
		}
		
		@Override
		public void run ()
		{
			while ( bluetooth.GetPartner () == null )
			{
				try {
					Thread.sleep( 1000 );
				} catch (InterruptedException e) {
					// Something wrong happened
				}
			}
			
			LaunchChat ();
		}
	}
	
	/*
	 * BluetoothManager coordinates
	 * all bluetooth related activities.
	*/
	public class BluetoothManager
	{
		// Bluetooth adapter
		public BluetoothAdapter m_Adapter;
		
		// Bluetooth device list
		private ArrayList <BluetoothDevice> m_AdapterList;
		
		// Bluetooth list adapter
		private ArrayAdapter <String> m_BluetoothAdapter;
		
		// Context of manager
		private Context m_Context;
		
		// A list of messages received
		public ArrayList <String> m_MessageList;
		
		// An adapter for messages received
		public ArrayAdapter <String> m_MessageAdapter; 
		
		// Socket of partner
		private BluetoothSocket m_Partner;
		
		// Receiver
		private final BroadcastReceiver m_Receiver = new BroadcastReceiver () {
			// When a new device is received
			public void onReceive ( Context context, Intent intent )
			{
				String action = intent.getAction ();
				
				// A device has been detected
				if ( BluetoothDevice.ACTION_FOUND.equals ( action ) )
				{
					// Extract the bluetooth device
					BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					
					// Add the device
					m_AdapterList.add(device);
					
					if ( m_BluetoothAdapter != null )
						m_BluetoothAdapter.add("Name: " + device.getName () + " Address: " + device.getAddress ());
				}
			}
		};
				
		// Request codes
		public static final int ENABLE_BLUETOOTH = 100;
		
		// constructor
		public BluetoothManager ( Context context ) 
		{
			m_Adapter = null;
			m_AdapterList = new ArrayList <BluetoothDevice> ();
			m_MessageList = new ArrayList <String> ();
			m_Context = context;
			m_BluetoothAdapter = null;
			m_Partner = null;
		}	
		
		// Initialize bluetooth
		public boolean Initialize ()
		{
			m_Adapter = BluetoothAdapter.getDefaultAdapter();
			
			// Bluetooth doesn't exist
			if ( m_Adapter == null )
				return false;
			else
			{
				// Bluetooth is disabled
				if ( m_Adapter.isEnabled() == false )
				{					
					Intent enableBT = new Intent ( BluetoothAdapter.ACTION_REQUEST_ENABLE );
					startActivityForResult ( enableBT, ENABLE_BLUETOOTH );
					return true;
				}
				else
					return true;
			}
		}
		
		// Locate all devices in premise
		public void LocateAllDevices ()
		{
			// Make the device discoverable for 5 minutes
			Intent discoverable = new Intent ( BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE );
			discoverable.putExtra ( BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300 );
			startActivity ( discoverable );
			
			// Register the receiver
			IntentFilter filter = new IntentFilter ( BluetoothDevice.ACTION_FOUND );			
			registerReceiver ( m_Receiver, filter );
			m_Adapter.startDiscovery();
		}
		
		public void Connect ( String MacAddress )
		{
			// Check to see if any of the devices are known and paired
			Set<BluetoothDevice> m_PairedDevices = m_Adapter.getBondedDevices ();
			/*
			if ( m_PairedDevices.size () > 0 )
			{
				// Add the device to the list
				for ( BluetoothDevice device : m_PairedDevices )
				{
				}
			}*/
			
			server = new BluetoothServerManager ( m_Adapter, 
												  MacAddress, 
												  m_Context );			
			client = new BluetoothClientManager ( m_Adapter.getRemoteDevice ( MacAddress ), 
												  m_Adapter, 
												  m_Context );
			
			server.start ();
			client.start ();
		}
				
		// Adapter has encountered an error
		public void CancelAdapter ()
		{
			m_Adapter = null;
		}
		
		// Unregister receiver on exit
		public void UnregisterReceiver ()
		{
			unregisterReceiver ( m_Receiver );
		}
		
		// Get the adapter list
		public ArrayList<String> GetBluetoothList ()
		{						
			ArrayList<String> ListToString = new ArrayList <String> ();
			
			for ( BluetoothDevice device : m_AdapterList )
				ListToString.add( "Name: " + device.getName () + " Address: " + device.getAddress () );
			
			return ListToString;
		}
		
		// Set the array adapter
		public void SetArrayAdapter ( ArrayAdapter<String> Adapter )
		{
			m_BluetoothAdapter = Adapter;
		}
		
		// Start scanning for devices
		public void Scan ()
		{
			m_Adapter.startDiscovery ();
		}
		
		// Set the partner's socket
		public void SetPartner ( BluetoothSocket socket )
		{
			m_Partner = socket;
		}
		
		// Add a message
		public void AddMessage ( String Message )
		{
			if ( m_MessageAdapter != null )
				m_MessageAdapter.add( Message );
		}
		
		// Get the partner's socket
		public BluetoothSocket GetPartner ()
		{
			return m_Partner;
		}
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() 
		{
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) 
		{
			// Obtain view
			final View rootView = inflater.inflate(R.layout.fragment_main, container,	false);
						
			// Obtain the ListView
			ListView bluetoothList = (ListView) rootView.findViewById( R.id.BluetoothList );	
			
			bluetoothList.setOnItemClickListener( new OnItemClickListener () {

				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) 
				{
					// Extract the Mac Address
					String selectedItem = (String) parent.getItemAtPosition(position);
					String macAddress = selectedItem.substring(
							selectedItem.indexOf( "Address: ") + "Address: ".length () );
					
					// Attempt to connect to the user
					bluetooth.Connect(macAddress);
					
					// Initialize the chatting system
					chat = new InitializeChat ( rootView.getContext () );
					chat.start ();
				}
			});
			
			// Create a BluetoothManager instance
			bluetooth = new BluetoothManager ( rootView.getContext () );
			
			// Obtain the scan button
			TextView Scan = (TextView) rootView.findViewById(R.id.Scan);
			
			Scan.setOnClickListener( new OnClickListener () {

				public void onClick(View v) {
					bluetooth.Scan ();					
				}
				
			});
			
			// Setup the adapter
			ArrayAdapter <String> listAdapter = 
				new ArrayAdapter <String> ( rootView.getContext (), 
											R.layout.listrow, 
											bluetooth.GetBluetoothList() );
			
			// Setup the adapter
			bluetooth.SetArrayAdapter( listAdapter );
			
			// Attempt to initialize bluetooth
			if ( !bluetooth.Initialize () )
				MessageBox ( "Please fix your bluetooth settings.", rootView.getContext () );
			else
			{
				// If bluetooth is setup properly
				if ( ( bluetooth.m_Adapter != null ) &&
					 ( bluetooth.m_Adapter.isEnabled () ) )
				{					
					// Bluetooth has been initialized successfully.
					bluetooth.LocateAllDevices ();
				}
				else
					MessageBox ( "Something is wrong with your bluetooth.", rootView.getContext () );
			}
								
			// Set the adapter
			bluetoothList.setAdapter( listAdapter );
			
			// Return
			return rootView;
		}
	}

	/*
	 * A placeholder fragment for the messaging component
	*/
	public class ChatholderFragment extends Fragment {

		public ChatholderFragment() 
		{
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) 
		{
			// Obtain view
			final View rootView = inflater.inflate(R.layout.chat_main, container, false);
			
			// Setup the chat manager
			chatManager = new ConnectionManager ( bluetooth.GetPartner(), rootView );
			
			// Obtain the ListView
			ListView ChatList = (ListView) rootView.findViewById( R.id.ChatList );	
			
			// Obtain the send button
			TextView Send = (TextView) rootView.findViewById(R.id.Send);
			
			// Setup the send button
			Send.setOnClickListener( new OnClickListener () {

				public void onClick(View v) {
					EditText Msg = (EditText) rootView.findViewById ( R.id.message );
					String message = "Me: " + Msg.getText().toString ();
					chatManager.write( Msg.getText().toString ().getBytes() );
					bluetooth.AddMessage(message);
					Msg.setText("");
				}
				
			});
			
			// Setup the adapter
			bluetooth.m_MessageAdapter = 
				new ArrayAdapter <String> ( rootView.getContext (), 
											R.layout.chatrow, 
											bluetooth.m_MessageList );
								
			// Set the adapter
			ChatList.setAdapter( bluetooth.m_MessageAdapter );
			
			// Run the manager
			chatManager.start ();
						
			// Return
			return rootView;
		}
	}
}
