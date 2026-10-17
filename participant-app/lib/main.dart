import 'package:flutter/material.dart';

import 'dev/component_gallery.dart';
import 'ui/ui.dart';

void main() {
  runApp(const CrowdedApp());
}

class CrowdedApp extends StatelessWidget {
  const CrowdedApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Crowded',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      // Replace with the participant screens once they exist.
      home: const ComponentGallery(),
    );
  }
}
