# Gestify for On-Device Deep Learning
- Used Andriod Studio (Ladybug) while developing
- Spotify needs to be downloaded on device where the app is running
- Note: Will need Spotify Client ID in local.properties

#### Brief Explanation
- With permission from the user,the app uses the device’s camera using Andriods’s CameraX. Frames are captured in real time and is processed into format suitable for the ML model. In terms of processing, we normalize the pixels and reshape the input image to be 640 x 640 which is what the model expects.
- We then feed the processed image into our pre-trained model (TFLite or ONNX). The main branch uses a ONNX model, but the branch tflite-model-branch uses an TFLite model.
- We take the output from the model which includes coordinates of detected objects, class labels, and confidence scores.
- We then take use the top detection as long as it meets the confidence threshold.
- The app then maps classified gestures to two types of controls:
  - Android system actions such as volume up/down
  - Spotify playback commands such as play/pause which use the Spotify Android SDK
- Here is the current mapping:
  - One -> Play
  - Two Up -> Pause
  - Fist -> Mute
  - Palm -> Unmute
  - Like -> Volume Up
  - Dislike -> Volume Down
  - Three -> Rewind
  - Four -> Skip
  - Middle Finger -> Surprise!
- The UI updates with the gesture label, corresponding action, and the status of the track.

#### Code Overview
There are three main files to take note of:

**SpotifyConnection.kt**
  
This class is responsible for managing the Spotify connection using the Spotify Andriod SDK.
- Handles Spotify authentication and connection
- Manages playback control (play, pause, skip...)
- Provides volume control (up, mute...)

**ObjectDetectionHelper.kt**
  
This class is used for real-time gesture dection using either an ONNX model or TFLite model
- Loads and runs a pre-trained gesture recogntion model
- Processes output and finds detections meeting the confidence threshold
- Maps class id of gestures to labels

**CameraFragment.kt**

This class is a fragment for real-time gesture-controlled Spotify playback using the device camera and model gesture detections
- Captures and processes live camera feed for input into model
- Uses detections from model to map to music playback controls


Flow of the Application:
- CameraFragment captures frame from device camera. Processes the frames and feeds into the model using the ObjectDetectionHelper.
- The ObjectDetectionHelper returns the top detections that meet the confidence threshold to the CameraFragment
- The CameraFragment takes the top detection from the list and uses that detection to map to the actions mentioned above using SpotifyConnection. Additionally, CameraFragment updates the UI with the detection and corresponding action.

#### Here is an example of the current app being run on a Pixel 4 using the emulator.
<img src=https://github.com/user-attachments/assets/3c20eb67-a54c-4838-adf3-6386ae185e1d alt="Description" width="300">


Pause and Play

https://github.com/user-attachments/assets/46b445dc-fa32-4633-8375-9e04e6eb1620


Volume up and down


https://github.com/user-attachments/assets/959286ca-61f9-4018-9ca9-7adc78cfec33


Mute and Unmute


https://github.com/user-attachments/assets/a05ae0fc-5d07-4715-a2f9-dca99df06927


Rewind


https://github.com/user-attachments/assets/21027247-02c2-47cd-9751-bcd92cecea12

Surprise


https://github.com/user-attachments/assets/78ed22fe-20e0-4945-89b4-1d2a645e1c48

Skip


https://github.com/user-attachments/assets/49a2becd-33dd-4ecf-8f23-514ba8fc14ac











