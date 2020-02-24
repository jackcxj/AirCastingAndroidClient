## COMP90055 Research Project - Code Refactor in Aircasting Application

### Supervisor: Prof. Richard Sinnott
### Designers: Ding Wu & Xiangjie Cui & Ze Yang
### Organization: The University of Melbourne

Aircasting is an online platform for environmental data visualization, which uses sensors Airbeam and Android applications to collect, upload, and display data. However, the application, completed in 2011, is out of service due to a lack of necessary maintenance and updates. Our project focuses on code refactoring for Aircasting's Android application. Its main content is to move Google Maps from version 1 to version 3 and retain its external activities and functions. At the same time, we also combined a new feature called Automatic real-time upload. To learn more about the platform visit [aircasting.org](http://aircasting.org).

### Let it compile and run properly in android device

1. You need to apply a new Google API Key and put it into res/values/string.xml where string name="API_KEY". Here is a guide for you about applying Google API Key: https://developers.google.com/maps/documentation/javascript/get-api-key.
2. If you want to use Automatic Real-time Upload Function, you need to run Server.java(under the project directory) in a separate process(We use Intellij to run this code separately as a server). You can also change the server port inside the code. After that, you also needs to modify the server ip and port in the code line 159 of StartMobileSessionActivity.class.

### Newly Added Code & Modification

Based on the open source code: https://github.com/HabitatMap/AirCastingAndroidClient, we have implemented the map-related features by adding NewAirCastingMapActivity, NewHeatMapOverlay(pl.llp.aircasting.screens.stream.map), NewAirCastingActivity, NewAirCastingBaseActivity(pl.llp.aircasting.screens.stream.base) and NewRoboMapActivityWithProgress(pl.llp.aircasting.screens.common.base) classes. 
We also achieve Automatic Real-time Upload Function by modifying the original code StartMobileSessionActivity.class(pl.llp.aircasting.screens.sessionRecord). 

### Contact

The original source code link is https://github.com/HabitatMap/AirCastingAndroidClient.
If you have any questions about the modified part, please contact xiangjiec@student.unimelb.edu.au.
