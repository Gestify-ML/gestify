# Gestify for On-Device Deep Learning
- Used Andriod Studio (Ladybug) while developing
- Spotify needs to be downloaded on device where the app is running
- Note: Will need Spotify Client ID in local.properties

#### Brief Explanation
- With permission from the user, the app uses the camera feed as input for a TFLite model trained to classify 10 gestures (middle finger, dislike, fist, four, like, one, palm, three, two up, and no gesture)
- These gestures are then mapped to different music playback controls. As of right now, the current mapping is:
  - One -> Play
  - Two Up -> Pause
  - Fist -> Mute
  - Palm -> Unmute
  - Like -> Volume Up
  - Dislike -> Volume Down
  - Three -> Rewind
  - Four -> Skip
  
#### Here is an example of the current app being run on a Pixel 4 using the emulator.
<img src=https://github.com/user-attachments/assets/3c20eb67-a54c-4838-adf3-6386ae185e1d alt="Description" width="300">

