package com.example.streamvideoapp;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.http.conn.util.InetAddressUtils;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback
{
	static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
	static int FRAME_PERIOD = 100; //Frame period of the video to stream, in ms
	  
	private final static String TAG = "MainActivity";
	protected SurfaceView mainView;
	protected SurfaceView myView;
	protected Camera camera;
	protected boolean isPreviewing;
	protected boolean hasSnap;
	protected int cameraId;
	protected SocketServer socketServer;
	protected Sender sender;
	protected ArrayList<byte[]> outputBuffer = new ArrayList<byte[]>();
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
	public class SocketServer extends java.lang.Thread {
		 
	    private boolean OutServer = false;
	    private ServerSocket server;
	    private final int ServerPort = 8080;// 要監控的port
	 
	    public SocketServer() {
	        try {
	            server = new ServerSocket(ServerPort);
	        } catch (java.io.IOException e) {
	        	Log.e(TAG, "IOException :" + e.toString());
	        }
	    }
	    
	    /**
	     * Get IP address from first non-localhost interface
	     * @param ipv4  true=return ipv4, false=return ipv6
	     * @return  address or empty string
	     */
	    public String getIPAddress(boolean useIPv4) {
	        try {
	            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
	            for (NetworkInterface intf : interfaces) {
	                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
	                for (InetAddress addr : addrs) {
	                    if (!addr.isLoopbackAddress()) {
	                        String sAddr = addr.getHostAddress().toUpperCase();
	                        boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr); 
	                        if (useIPv4) {
	                            if (isIPv4) 
	                                return sAddr;
	                        } else {
	                            if (!isIPv4) {
	                                int delim = sAddr.indexOf('%'); // drop ip6 port suffix
	                                return delim<0 ? sAddr : sAddr.substring(0, delim);
	                            }
	                        }
	                    }
	                }
	            }
	        } catch (Exception ex) { } // for now eat exceptions
	        return "";
	    }	    
	 
	    public void run() {
	    	Paint paint = new Paint();
	        Socket socket;
	        BufferedInputStream in;	        
	        final String ip = getIPAddress(true);
	        Log.e(TAG, "伺服器已啟動 :" + ip);
	        
	        MainActivity.this.runOnUiThread(new Runnable() {
				@Override
				public void run()
				{
					TextView tv = (TextView) MainActivity.this.findViewById(R.id.localTV);
					tv.setText("LocalCam(" + ip + "/" + ServerPort + ")");
				}
	        });
	        
	        while (!OutServer) {
	            socket = null;
	            try {
	            	socket = server.accept();
                    Log.e(TAG, "取得連線 : InetAddress = "
	                        + socket.getInetAddress());
	                socket.setSoTimeout(150000);
	                
	                //String path = Environment.getExternalStorageDirectory() + "/Remote.jpg";
	                //FileOutputStream fos = new FileOutputStream(path);
	 
	                in = new BufferedInputStream(socket.getInputStream());
	                
	                ByteArrayOutputStream imgBuf = new ByteArrayOutputStream();
	                byte[] b = new byte[1024];
	                int readBytes;
	                while ((readBytes = in.read(b)) > 0)// <=0的話就是結束了
	                {
	                	int imgEnd = readBytes-1;
	                	boolean foundImgEnd = false;
	                	//Log.e(TAG, "FIRST 2 BYTES:" + b[0] + "/" + b[1]);
	                    for (int i=0; i<readBytes-1; i++)
	                    {
	                    	if (b[i] == -1 && b[i+1] == -39)
	                    	{
	                    		imgEnd = i+1;
	                    		foundImgEnd = true;
	                    	}
	                    }
	                    imgBuf.write(b, 0, imgEnd+1);
	                    if (foundImgEnd)
	                    {
	                    	byte[] byteArray = imgBuf.toByteArray();	                		
	                		final Bitmap bm = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
	                		if (bm == null)
	                		{
	                			Log.e(TAG, "!!!! bitmap decode failed !!!!");
	                			imgBuf.reset();
	                			continue;
	                		}
	                		
	                		Canvas canvas = MainActivity.this.mainView.getHolder().lockCanvas();
	                		if (canvas != null)
	                		{
	                			canvas.drawBitmap(bm, 0, 0, paint);
	                			MainActivity.this.mainView.getHolder().unlockCanvasAndPost(canvas);
	                		}
	            			
	            			imgBuf.reset();	            			
	            			imgBuf.write(b, imgEnd+1, readBytes - imgEnd -1);
	                    }
	                }
	                
	                //fos.close();
	                
	                Log.e(TAG, "結束連線");
	                in.close();
	                in = null;
	                socket.close();
	                
	            } catch (java.io.IOException e) {
	            	Log.e(TAG, "IOException :" + e.toString());
	            	break;
	            }	 
	        }
	    }
	}
	
	public class Sender extends Thread
	{
		String ip;
		InetAddress ipAddr;
		int port;
		Socket sock;
		boolean initOK;
		boolean isRunning;
		OutputStream outputStream;
		
		public Sender(String ip, int port)
		{
			this.ip = ip;
			this.port = port;
			try {
				this.ipAddr = InetAddress.getByName(ip);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		}
		
		
		public void run()
		{
			sock = new Socket();
			try {
				sock.connect(new InetSocketAddress(ip, port));
				outputStream = sock.getOutputStream();
				isRunning = true;
			} catch (IOException e) {
				MainActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						String errMsg = 
								String.format(MainActivity.this.getString(R.string.connect_failed), ip, port);
						Toast.makeText(MainActivity.this, errMsg, Toast.LENGTH_SHORT).show();
					}
				});
				
				isRunning = false;
				return;
			}
			
			Log.e(TAG, "Sender start");
			while (isRunning)
			{
				boolean empty = false;
				byte[] data = null;
				
				synchronized(outputBuffer)
				{
					if (outputBuffer.size() == 0)
					{
						empty = true;
					}
					else
						data = outputBuffer.remove(0);
				}
				if (empty)
				{
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
					}
					continue;
				}
				try
				{
					outputStream.write(data);
				} catch (IOException e) {
					Log.e(TAG, "sock error");
					isRunning = false;
				}
			}
			try {
				sock.close();
			} catch (IOException e) {
			}
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
		socketServer = new SocketServer();
		socketServer.start();
		
		mainView = (SurfaceView) MainActivity.this.findViewById(R.id.mainView);
		myView = (SurfaceView) MainActivity.this.findViewById(R.id.myView);
		
		SurfaceHolder holder = myView.getHolder();
		holder.addCallback(this);	
		
		this.findViewById(R.id.sendBtn).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String ip = ((EditText)MainActivity.this.findViewById(R.id.ipET)).getText().toString();
				String portStr = ((EditText)MainActivity.this.findViewById(R.id.portET)).getText().toString();
				if (ip.length() == 0 || portStr.length() == 0)
				{
					Toast.makeText(MainActivity.this, "Please input remote ip/port", Toast.LENGTH_SHORT).show();
					return;
				}
				
				int port = Integer.parseInt(portStr);
				sender = new Sender(ip, port);
				sender.isRunning = true;
				sender.start();
			}
		});
		
	}
	
	@Override
	public void onPause()
	{
		if (sender != null)
			sender.isRunning = false;
		try {
			socketServer.server.close();
		} catch (IOException e) {
		}
		super.onPause();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		if (isPreviewing)
		{
			camera.stopPreview();
			isPreviewing = false;
		}
		try {
			setCameraDisplayOrientation(this, cameraId, camera);
			camera.setPreviewDisplay(holder);
			camera.setPreviewCallback(this);
			camera.startPreview();
			isPreviewing = true;
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		try
		{
			camera = openFrontFacingCamera();
			List<Size> camSizes = camera.getParameters().getSupportedPreviewSizes();
			int minWidth = Integer.MAX_VALUE;
			Size minSize = null;
			for (Size s : camSizes)
			{
				if (s.width < minWidth)
				{
					minSize = s;
					minWidth = s.width;
				}
			}
			Camera.Parameters parameters = camera.getParameters();
		    parameters.setPreviewSize(minSize.width, minSize.height);
		    camera.setParameters(parameters);
		    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(minSize.height, minSize.width);
			myView.setLayoutParams(params);			
			Log.e(TAG, "setPreviewSize:" + minSize.width + "/" + minSize.height);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	private Camera openFrontFacingCamera() {
	    int cameraCount = 0;
	    Camera cam = null;
	    Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
	    cameraCount = Camera.getNumberOfCameras();
	    for ( int camIdx = 0; camIdx < cameraCount; camIdx++ ) {
	        Camera.getCameraInfo( camIdx, cameraInfo );
	        if ( cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT  ) {
	            try {
	                cam = Camera.open( camIdx );
	                cameraId = camIdx;
	            } catch (RuntimeException e) {
	                Log.e(TAG, "Camera failed to open: " + e.getLocalizedMessage());
	            }
	        }
	    }

	    return cam;
	}
	
	public static void setCameraDisplayOrientation(Activity activity,
	         int cameraId, android.hardware.Camera camera) {
		if (Build.VERSION.SDK_INT <= 8)
		{
			return;
		}
		android.hardware.Camera.CameraInfo info =
	             new android.hardware.Camera.CameraInfo();
		android.hardware.Camera.getCameraInfo(cameraId, info);
	    int rotation = activity.getWindowManager().getDefaultDisplay()
	             .getRotation();
	    int degrees = 0;
	    switch (rotation) {
	        case Surface.ROTATION_0: degrees = 0; break;
	        case Surface.ROTATION_90: degrees = 90; break;
	        case Surface.ROTATION_180: degrees = 180; break;
	        case Surface.ROTATION_270: degrees = 270; break;
	    }

	    int result;
	    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
	        result = (info.orientation + degrees) % 360;
	        result = (360 - result) % 360;  // compensate the mirror
	    } else {  // back-facing
	        result = (info.orientation - degrees + 360) % 360;
	    }
	    camera.setDisplayOrientation(result);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		camera.setPreviewCallback(null);
		camera.stopPreview();
		camera.release();
		camera = null;
		isPreviewing = false;
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {		
		if (outputBuffer.size() <= 10)
		//if (!hasSnap)
		{
			try {
				Camera.Parameters parameters = camera.getParameters();
				int w = parameters.getPreviewSize().width;
				int h = parameters.getPreviewSize().height;
				Rect rect = new Rect(0, 0, w, h);
				
				//String path = Environment.getExternalStorageDirectory() + "/Test.jpg";
				//FileOutputStream stream = new FileOutputStream(path);
				
				YuvImage img = new YuvImage(data, ImageFormat.NV21, w, h, null);				
				img.compressToJpeg(rect, 10, baos);
				
				byte[] outputData = baos.toByteArray();
				synchronized(outputBuffer)
				{
					outputBuffer.add(outputData);
				}
				baos.reset();
				//stream.write(outputData);
				
				Log.e(TAG, "onPreviewFrame:" + outputData.length);
			} catch (Exception e) {
				e.printStackTrace();
				Log.e(TAG, "Save jpg failed:" + e.getMessage());
			}
			hasSnap = true;
		}
	}

}
