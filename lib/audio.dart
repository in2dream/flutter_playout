import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// See [play] method as well as example app on how to use.
class Audio {
  static const MethodChannel _audioChannel = MethodChannel('tv.mta/NativeAudioChannel');

  Audio._();

  static Audio _instance;
  static Audio instance() {
    if (_instance == null) {
      _instance = Audio._();
    }
    return _instance;
  }

  String _url;
  String _title;
  String _subtitle;
  Duration _position;
  bool _isLiveStream;
  String _cover;

  /// Plays given [url] with native player. The [title] and [subtitle]
  /// are used for lock screen info panel on both iOS & Android. Optionally pass
  /// in current [position] to start playback from that point. The
  /// [isLiveStream] flag is only used on iOS to change the scrub-bar look
  /// on lock screen info panel. It has no affect on the actual functionality
  /// of the plugin. Defaults to false.
  Future<void> play(String url,
      {String title = "",
      String subtitle = "",
      Duration position = Duration.zero,
      bool isLiveStream = false,
      double updateInterval = 0.1,
      String cover = ""}) async {
    if (_hasDataChanged(url, title, subtitle, position, isLiveStream)) {
      this._url = url;
      this._title = title;
      this._subtitle = subtitle;
      this._position = position;
      this._isLiveStream = isLiveStream;
      this._cover = cover;
      return _audioChannel.invokeMethod("play", <String, dynamic>{
        "url": url,
        "title": title,
        "subtitle": subtitle,
        "position": position.inMilliseconds,
        "isLiveStream": isLiveStream,
        "updateInterval": updateInterval ?? 0.1,
        "cover": cover,
      });
    }
  }

  bool _hasDataChanged(String url, String title, String subtitle, Duration position, bool isLiveStream) {
    return this._url != url ||
        this._title != title ||
        this._subtitle != subtitle ||
        this._position != position ||
        this._isLiveStream != isLiveStream;
  }

  Future<void> pause() async {
    return _audioChannel.invokeMethod("pause");
  }

  Future<void> reset() async {
    return _audioChannel.invokeMethod("reset");
  }

  Future<void> updatePlayIndex(int index) async {
    return _audioChannel.invokeMethod('updatePlayIndex', {'index': index});
  }

  Future<void> setQueue({List<MediaItem> items, int index = 0}) async {
    return _audioChannel.invokeMethod(
        'setQueue', {'queue': items.map((MediaItem item) => item.toMap()).toList(), 'currentIndex': index});
  }

  Future<void> seekTo(Duration position) async {
    return _audioChannel.invokeMethod("seekTo", <String, dynamic>{
      "second": (position.inMilliseconds / 1000.0).toDouble(),
    });
  }

  Future<void> dispose({bool clearNotification = false}) async {
    _instance = null;
    await _audioChannel.invokeMethod("dispose", <String, dynamic>{"clearNotification": clearNotification});
  }
}

class MediaItem {
  final String title;
  final String subtitle;
  final String author;
  final String url;
  final String cover;

  MediaItem({this.title, this.subtitle, this.author, @required this.url, this.cover});

  Map<String, dynamic> toMap() {
    return {'url': url, 'title': title ?? '', 'author': author ?? '', 'cover': cover};
  }
}
