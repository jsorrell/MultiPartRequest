package com.jsorrell.multipartrequest;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.os.AsyncTask;
import android.util.Log;

@SuppressWarnings("unused")
public class MultiPartRequest extends AsyncTask<URL,Void,String> {

  private static final String twoHyphens = "--";
  private static final String boundary = "*****" + Long.toString(System.currentTimeMillis()) + "*****";
  private static final String lineEnd = "\r\n";
  private static final int maxBufferSize = 1024*1024;
  private static final Set<String> fixedHttpHeaders = new HashSet<String>(Arrays.asList("method","content-type","host","content-length"));

  private ArrayList<Field> fields = new ArrayList<Field>();
  private Map<String,String> httpHeaders = new HashMap<String,String>();

  
  /**
   * Initializer for MultiPartRequest
   *
   * @see AsyncTask
   */ 
  public MultiPartRequest() {
		super();
		/* Default headers */
		httpHeaders.put("connection", "Keep-Alive");
		httpHeaders.put("user-agent", "Android MultiPart Request");
	}
  
  
  private class Field {
  	String name;
  	String mimeType = null;
  	byte[] data;
  	
  	protected Field(String name, String mimeType) {
  		this.name = name;
  		this.mimeType = mimeType;
  	}
  	
    Field(String name, String mimeType, byte[] data) {
  	  this(name,mimeType);
  	  this.data = data;
    }
  	
    Field(String name, String mimeType, String data) {
  	  this(name,mimeType,data.getBytes());
    }
    
    protected void writeFieldHeader(DataOutputStream outputStream) throws IOException{
    	outputStream.writeBytes(twoHyphens + MultiPartRequest.boundary + lineEnd);
      outputStream.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"" + lineEnd);
      outputStream.writeBytes("Content-Type: " + mimeType + lineEnd);
      outputStream.writeBytes(lineEnd);
    }
    
    protected void writeFieldFooter(DataOutputStream outputStream) throws IOException{
    	outputStream.writeBytes(lineEnd);
    }
    
    protected void writeField(DataOutputStream outputStream) throws IOException {
      writeFieldHeader(outputStream);
      outputStream.write(data, 0, data.length);
      writeFieldFooter(outputStream);
    }
    
  }
  
  private class FileField extends Field {
  	String fileName = null;
  	boolean streaming = false;
  	InputStream inputStream = null;
  	
  	protected FileField(String name, String mimeType, String fileName) {
  		super(name,mimeType);
  		this.fileName = fileName;
  	}
  	
  	FileField(String name, String mimeType, String fileName, byte[] data) {
  		super(name,mimeType,data);
  		this.fileName = fileName;
  	}
  	
  	FileField(String name, String mimeType, String fileName, String data) {
  	  this(name,mimeType,fileName,data.getBytes());
    }
  	
  	FileField(String name, String mimeType, String fileName, File f) throws FileNotFoundException {
  	  this(name,mimeType,fileName);
  	  inputStream = new FileInputStream(f);
  	  streaming = true;
  	}
  	FileField(String name, String mimeType, String fileName, InputStream stream) {
  	  this(name,mimeType,fileName);
  	  inputStream = stream;
  	  streaming = true;
    }
  	
  	@Override
  	protected void writeFieldHeader(DataOutputStream outputStream) throws IOException{
  		outputStream.writeBytes(twoHyphens + MultiPartRequest.boundary + lineEnd);
      outputStream.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"" + lineEnd);
      outputStream.writeBytes("Content-Type: " + mimeType + lineEnd);
      outputStream.writeBytes(lineEnd);
    }

  	protected void writeStream(DataOutputStream outputStream) throws IOException {
  		int bytesAvailable = inputStream.available();
      int bufferSize = Math.min(bytesAvailable, MultiPartRequest.maxBufferSize);
      byte[] buffer = new byte[bufferSize];
      int bytesRead = 0;
      while((bytesRead = inputStream.read(buffer, 0, bufferSize)) > 0) {
          outputStream.write(buffer, 0, bufferSize);
          bytesAvailable = inputStream.available();
          bufferSize = Math.min(bytesAvailable, maxBufferSize);
      }
  	}
  	
  	@Override
  	protected void writeField(DataOutputStream outputStream) throws IOException {
      writeFieldHeader(outputStream);
      if (streaming)
      	writeStream(outputStream);
      else
      	outputStream.write(data, 0, data.length);
      writeFieldFooter(outputStream);
  	}

  }
  
  @Override
	protected String doInBackground(URL... urls) {
		if (urls.length < 1) throw new IllegalArgumentException();
		URL url = urls[0];
    HttpURLConnection connection = null;
    DataOutputStream outputStream = null;
    InputStream inputStream = null;
    
    String result = "";
    
    int bytesRead, bytesAvailable, bufferSize;
    byte[] buffer;
    int maxBufferSize = 1*1024*1024;
    
    try {
      connection = (HttpURLConnection)url.openConnection();
    } catch (IOException ex) {
    	Log.e("MultiPartRequestError", "Couldn't open connection.", ex);
    	return "MultiPart Request error";
    }
      
      
    connection.setDoInput(true);
    connection.setDoOutput(true);
    connection.setUseCaches(false);
    
    
    try {
    	connection.setRequestMethod("POST");
    } catch (ProtocolException ex) {
    	if (BuildConfig.DEBUG)
    		throw new AssertionError();
    }
    		
    connection.setRequestProperty("Host",url.getHost());
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary="+boundary);
    
    for (String headerName : httpHeaders.keySet()) {
    	connection.setRequestProperty(headerName, httpHeaders.get(headerName));
    }
    try {
    	outputStream = new DataOutputStream(connection.getOutputStream());
    } catch (IOException ex) {
    	Log.e("MultiPartRequestError", "Couldn't send data. Is the URL correct?", ex);
    	return "MultiPart Request error";
    }
    
    try {
      for (Field field : fields) {
      	if (field instanceof FileField)
      		((FileField)field).writeField(outputStream);
      	else
      		field.writeField(outputStream);
      }
  
      outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
      outputStream.flush();
      outputStream.close();
    } catch (IOException ex) {
    	Log.e("MultiPartRequestError", "Couldn't write to HTTP stream.", ex);
    }
    try {
      inputStream = connection.getInputStream();
      result = convertStreamToString(inputStream);
      inputStream.close();
    } catch (IOException ex) {
    	Log.e("MultiPartRequestError", "Couldn't read server response.", ex);
    }
    
    return result;
  }

  private static String convertStreamToString(InputStream is) throws IOException {
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      StringBuilder sb = new StringBuilder();

      String line = null;
      try {
          while ((line = reader.readLine()) != null) {
              sb.append(line);
          }
      } finally {
      	is.close();
      }
      return sb.toString();
  }
  
  /**
   * Adds a byte array as a field of the multipart request
   *
   * @param  name     the name of the field in the multipart data
   * @param  mimeType the name of the mime-type of data
   * @param  data     a byte array containing the data corresponding to name
   */ 
  public void addField(String name, String mimeType, byte[] data) {
  	if (name == null || mimeType == null || data == null) throw new IllegalArgumentException();
		this.fields.add(new Field(name,mimeType,data));
	}
  
  /**
   * Adds a string as a field of the multipart request
   *
   * @param  name     the name of the field in the multipart data
   * @param  mimeType the name of the mime-type of data
   * @param  data     a string containing the data corresponding to name
   */ 
  public void addField(String name, String mimeType, String data) {
  	if (name == null || mimeType == null || data == null) throw new IllegalArgumentException();
		this.fields.add(new Field(name,mimeType,data));
	}
  
  /**
   * Adds a byte array as a field of the multipart request
   *
   * @param  name     the name of the field in the multipart data
   * @param  mimeType the name of the mime-type of data
   * @param  fileName the name of the data file
   * @param  data     a byte array containing the data corresponding to name
   */ 
  public void addField(String name, String mimeType, String fileName, byte[] data) {
  	if (name == null || mimeType == null || data == null) throw new IllegalArgumentException();
		this.fields.add(new FileField(name,mimeType,fileName,data));
	}
  
  /**
   * Adds a file as a field of the multipart request
   *
   * @param  name     the name of the field in the multipart data
   * @param  mimeType the name of the mime-type of data
   * @param  fileName the name of the data file
   * @param  data     a File containing the data corresponding to name
   * @see    File
   */ 
  public void addField(String name, String mimeType, String fileName, File data) throws FileNotFoundException {
  	if (name == null || mimeType == null || fileName == null || data == null) throw new IllegalArgumentException();
  	this.fields.add(new FileField(name,mimeType,fileName,data));
	}
  
  /**
   * Adds an InputStream as a field of the multipart request. This reads the stream as it is sent to minimize memory usage
   *
   * @param  name     the name of the field in the multipart data
   * @param  mimeType the name of the mime-type of data
   * @param  fileName the name of the data file
   * @param  data     a byte array containing the data corresponding to name
   * @see InputStream
   */ 
  public void addField(String name, String mimeType, String fileName, InputStream data) {
  	if (name == null || mimeType == null || fileName == null || data == null) throw new IllegalArgumentException();
		this.fields.add(new FileField(name,mimeType,fileName,data));
	}
  
  /**
   * Adds or modifies an http header for the request
   *
   * @param  name the name of the header
   * @param  val  the value of the header
   * @see    #unsetHeader(String)
   */ 
  public void setHeader(String name, String val)
  {
  	if (name == null || val == null) throw new IllegalArgumentException();
  	String lowerName = name.toLowerCase(Locale.ENGLISH);
  	if (MultiPartRequest.fixedHttpHeaders.contains(lowerName)) {
  		Log.w("MultipartRequest","Cannot change \"" + name + "\" header");
  		return;
  	}
  	httpHeaders.put(lowerName, val);
  }
  
  /**
   * Removes an http header for the request
   *
   * @param  name the name of the header
   * @see    #setHeader(String, String)
   */ 
  public void unsetHeader(String name)
  {
  	if (name == null) throw new IllegalArgumentException();
  	httpHeaders.remove(name.toLowerCase(Locale.ENGLISH));
  }
}
