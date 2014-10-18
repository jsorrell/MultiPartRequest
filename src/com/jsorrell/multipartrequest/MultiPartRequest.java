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

  public MultiPartRequest() {
		super();
		/* Default headers */
		httpHeaders.put("Connection", "Keep-Alive");
		httpHeaders.put("User-Agent", "Android MultiPart Request");
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
	public String doInBackground(URL... urls) {
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
      
      connection.setDoInput(true);
      connection.setDoOutput(true);
      connection.setUseCaches(false);
      
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Host",url.getHost());
      connection.setRequestProperty("Content-Type", "multipart/form-data; boundary="+boundary);
      
      for (String headerName : httpHeaders.keySet()) {
      	connection.setRequestProperty(headerName, httpHeaders.get(headerName));
      }
     
      outputStream = new DataOutputStream(connection.getOutputStream());
      
      for (Field field : this.fields) {
      	if (field instanceof FileField)
      		((FileField)field).writeField(outputStream);
      	else
      		field.writeField(outputStream);
      }

      outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
      
      inputStream = connection.getInputStream();
      result = this.convertStreamToString(inputStream);
      
      inputStream.close();
      outputStream.flush();
      outputStream.close();
      
      return result;
    } catch(Exception e) {
      Log.e("MultipartRequest","Multipart Form Upload Error",e);
        e.printStackTrace();
      return "multipart error";
    }
  }

  private String convertStreamToString(InputStream is) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      StringBuilder sb = new StringBuilder();

      String line = null;
      try {
          while ((line = reader.readLine()) != null) {
              sb.append(line);
          }
      } catch (IOException e) {
              e.printStackTrace();
      } finally {
          try {
              is.close();
          } catch (IOException e) {
              e.printStackTrace();
          }
      }
      return sb.toString();
  }
  
  public void addField(String name, String mimeType, byte[] data) {
  	if (name == null || mimeType == null || data == null) throw new IllegalArgumentException();
		this.fields.add(new Field(name,mimeType,data));
	}
  
  public void addField(String name, String mimeType, String data) {
  	if (name == null || mimeType == null || data == null) throw new IllegalArgumentException();
		this.fields.add(new Field(name,mimeType,data));
	}
  
  public void addField(String name, String mimeType, String fileName, byte[] data) {
  	if (name == null || mimeType == null || data == null) throw new IllegalArgumentException();
		this.fields.add(new FileField(name,mimeType,fileName,data));
	}
  
  public void addField(String name, String mimeType, String fileName, File data) throws FileNotFoundException {
  	if (name == null || mimeType == null || fileName == null || data == null) throw new IllegalArgumentException();
  	this.fields.add(new FileField(name,mimeType,fileName,data));
	}
  
  public void addField(String name, String mimeType, String fileName, InputStream data) {
  	if (name == null || mimeType == null || fileName == null || data == null) throw new IllegalArgumentException();
		this.fields.add(new FileField(name,mimeType,fileName,data));
	}
  
  public void addHeader(String name, String val)
  {
  	if (name == null || val == null) throw new IllegalArgumentException();
  	if (MultiPartRequest.fixedHttpHeaders.contains(name.toLowerCase(Locale.ENGLISH))) {
  		Log.w("MultipartRequest","Cannot change \"" + name + "\" header");
  		return;
  	}
  	httpHeaders.put(name, val);
  }
}
