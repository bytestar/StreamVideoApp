// Source code from
// http://www.csee.umbc.edu/~pmundur/courses/CMSC691C/lab5-kurose-ross.html
package com.jemi.streamvideoapp;

//class RTPpacket

public class RTPpacket{

  //size of the RTP header:
  static int HEADER_SIZE = 12;

  //Fields that compose the RTP header
  public int Version;
  public int Padding;
  public int Extension;
  public int CC;
  public int Marker;
  public int PayloadType;
  public int SequenceNumber;
  public int TimeStamp;
  public int Ssrc;
  
  //Bitstream of the RTP header
  public byte[] header;

  //size of the RTP payload
  public int payload_size;
  //Bitstream of the RTP payload
  public byte[] payload;
  


  //--------------------------
  //Constructor of an RTPpacket object from header fields and payload bitstream
  //--------------------------
  public RTPpacket(int PType, int Framenb, int Time, boolean marker, byte[] data, int offset, int data_length){
    //fill by default header fields:
    Version = 2;
    Padding = 0;
    Extension = 0;
    CC = 0;
    Marker = marker ? 1 : 0;
    Ssrc = 0;

    //fill changing header fields:
    SequenceNumber = Framenb;
    TimeStamp = Time;
    PayloadType = PType;
    
    //build the header bistream:
    //--------------------------
    header = new byte[HEADER_SIZE];

    header[0] = (byte)((Version << 6) | (Padding << 5) | (Extension << 4) | (CC)); 
    header[1] = (byte)(Marker << 7 | PayloadType);
    header[2] = (byte)(SequenceNumber & 0xFFFF0000 >> 16);
    header[3] = (byte)(SequenceNumber & 0xFFFF);
    header[4] = (byte)((TimeStamp & 0xFF000000) >> 24);
    header[5] = (byte)((TimeStamp & 0xFF0000) >> 16);
    header[6] = (byte)((TimeStamp & 0xFF00) >> 8);
    header[7] = (byte)((TimeStamp & 0xFF));
    header[8] = (byte)((Ssrc & 0xFF000000) >> 24);
    header[9] = (byte)((Ssrc & 0xFF0000) >> 18);
    header[10] = (byte)((Ssrc & 0xFF00) >> 8);
    header[11] = (byte)((Ssrc & 0xFF));

    //fill the payload bitstream:
    //--------------------------
    payload_size = data_length;
    payload = new byte[data_length];

    //fill payload array of byte from data (given in parameter of the constructor)
    for (int i=0; i<data_length; i++)
    	payload[i] = data[offset+i];

    // ! Do not forget to uncomment method printheader() below !

  }
    
  //--------------------------
  //Constructor of an RTPpacket object from the packet bistream 
  //--------------------------
  public RTPpacket(byte[] packet, int packet_size)
  {
    //fill default fields:
    Version = 2;
    Padding = 0;
    Extension = 0;
    CC = 0;
    Marker = 0;
    Ssrc = 0;

    //check if total packet size is lower than the header size
    if (packet_size >= HEADER_SIZE) 
    {
		//get the header bitsream:
		header = new byte[HEADER_SIZE];
		for (int i=0; i < HEADER_SIZE; i++)
		  header[i] = packet[i];
	
		//get the payload bitstream:
		payload_size = packet_size - HEADER_SIZE;
		payload = new byte[payload_size];
		for (int i=HEADER_SIZE; i < packet_size; i++)
		  payload[i-HEADER_SIZE] = packet[i];
	
		//interpret the changing fields of the header:
		Marker = header[1] & 128;
		PayloadType = header[1] & 127;
		SequenceNumber = header[3] + 256*header[2];
		TimeStamp = (header[7]) + 256*(header[6]) + 65536*(header[5]) + 16777216*(header[4]);
    }
 }

  //--------------------------
  //getpayload: return the payload bistream of the RTPpacket and its size
  //--------------------------
  public int getpayload(byte[] data) {

    for (int i=0; i < payload_size; i++)
      data[i] = payload[i];

    return(payload_size);
  }

  //--------------------------
  //getpayload_length: return the length of the payload
  //--------------------------
  public int getpayload_length() {
    return(payload_size);
  }

  //--------------------------
  //getlength: return the total length of the RTP packet
  //--------------------------
  public int getlength() {
    return(payload_size + HEADER_SIZE);
  }

  //--------------------------
  //getpacket: returns the packet bitstream and its length
  //--------------------------
  public int getpacket(byte[] packet)
  {
    //construct the packet = header + payload
    for (int i=0; i < HEADER_SIZE; i++)
	packet[i] = header[i];
    for (int i=0; i < payload_size; i++)
	packet[i+HEADER_SIZE] = payload[i];

    //return total size of the packet
    return(payload_size + HEADER_SIZE);
  }

  //--------------------------
  //gettimestamp
  //--------------------------

  public int gettimestamp() {
    return(TimeStamp);
  }

  //--------------------------
  //getsequencenumber
  //--------------------------
  public int getsequencenumber() {
    return(SequenceNumber);
  }

  //--------------------------
  //getpayloadtype
  //--------------------------
  public int getpayloadtype() {
    return(PayloadType);
  }

  public boolean getmarker() {
	  return Marker > 0;
  }

  //--------------------------
  //print headers without the SSRC
  //--------------------------
  public void printheader()
  {
    //TO DO: uncomment
    /*
    for (int i=0; i < (HEADER_SIZE-4); i++)
      {
	for (int j = 7; j>=0 ; j--)
	  if (((1<= 0)
      return(nb);
    else
      return(256+nb);
      */
  }

}

