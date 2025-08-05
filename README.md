DJI Drone Hub
Built using DJI MSDK v5

An Android application for livestreaming video and data from DJI drones. For compatibility, please check DJI official website (https://developer.dji.com/doc/mobile-sdk-tutorial/en/)

This app allows you to open 2 real-time streams simultaneously:
1. Video stream using RTSP (may select between TCP/UDP)
2. Data stream using UDP

An example of stream receiver is provided at https://github.com/Equisetum-sp/DJI-Drone-hub-stream-receiver


How to run application:
1. Create a DJI developer account
2. Go to https://developer.dji.com/user
3. Click "CREATE APP", put "com.dji.sampleV5.aircraft" (without quotation) in package name field. Other fields may be filled based on needs
4. Activate the application through email sent by DJI
5. Select the File > Open... at the tool bar of Android studio, and import the android-sdk-v5-as project.
6. In the source code, open "SampleCode-V5/android-sdk-v5-as/gradle.properties". Replace "AIRCRAFT_API_KEY" value with the API key
7. Compile and launch on Android phone
