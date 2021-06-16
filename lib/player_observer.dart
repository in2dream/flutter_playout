import 'dart:async';

import 'package:flutter/services.dart';

/// Use with Video or Audio widget to get player notifications such as
/// [onPlay], [onPause] etc. See example on how to use.
mixin PlayerObserver {
  Future<void> listenForVideoPlayerEvents(int viewId) async {
    EventChannel eventChannel = EventChannel("tv.mta/NativeVideoPlayerEventChannel_$viewId", JSONMethodCodec());
    eventChannel.receiveBroadcastStream().listen(_processEvent);
  }

  Future<void> listenForAudioPlayerEvents() async {
    EventChannel eventChannel = EventChannel("tv.mta/NativeAudioEventChannel", JSONMethodCodec());
    eventChannel.receiveBroadcastStream().listen(_processEvent);
  }

  /// Override this method to get notifications when media is paused.
  void onPause() {/* user implementation */}

  /// Override this method to get notifications when media is played.
  void onPlay() {/* user implementation */}

  /// Override this method to get notifications when media has finished playing.
  void onComplete() {/* user implementation */}

  /// Override this method to get update when playhead moves. This method
  /// fires every second with [position] as seconds.
  void onTime(Duration position) {/* user implementation */}

  /// Override this method to get notifications when a seek operation has
  /// finished. This will occur when user finishes scrubbing media.
  /// [position] is position in seconds before seek started.
  /// [offset] is seconds after seek processed.
  void onSeek(Duration position, Duration offset) {/* user implementation */}

  /// Override this method to get notifications when media duration is
  /// set or changed.
  /// [duration] is in milliseconds. Returns -1 for live stream
  void onDuration(Duration duration) {/* user implementation */}

  /// Override this method to get errors thrown by the player
  void onError(String error) {/* user implementation */}

  void onPlayQueueItem(int index) {/* user implementation */}

  void _processEvent(dynamic event) async {
    String eventName = event["name"];

    switch (eventName) {

      /* onPause */
      case "onPause":
        onPause();
        break;

      /* onPlay */
      case "onPlay":
        onPlay();
        break;

      /* onComplete */
      case "onComplete":
        onComplete();
        break;

      /* onTime */
      case "onTime":
        onTime(Duration(milliseconds: event["time"].toInt()));
        break;

      case "onPlayQueueItem":
        onPlayQueueItem(event["index"].toInt());
        break;

      /* onSeek */
      case "onSeek":

        /* position of the player before the player seeks (in seconds) */
        int position = (event["position"]).toInt();

        /* requested position to seek to (in seconds) */
        int offset = (event["offset"]).toInt();

        onSeek(Duration(milliseconds: position), Duration(milliseconds: offset));

        break;

      case "onDuration":
        onDuration(Duration(milliseconds: (event["duration"]).toInt()));
        break;

      case "onError":
        onError(event["error"]);
        break;

      default:
        break;
    }
  }
}
