import SwiftUI
import UIKit

struct ContentView: View {
    @State private var showScanner = false
    @State private var scannedImages: [UIImage] = []

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {

                if let image = scannedImages.first {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(maxHeight: 450)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                } else {
                    VStack(spacing: 12) {
                        Image(systemName: "doc.viewfinder")
                            .font(.system(size: 60))

                        Text("No receipt scanned yet")
                            .foregroundStyle(.secondary)
                    }
                }

                Button {
                    showScanner = true
                } label: {
                    Label(
                        scannedImages.isEmpty ? "Scan Receipt" : "Scan Again",
                        systemImage: "camera.viewfinder"
                    )
                    .frame(maxWidth: .infinity)
                    .padding()
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
            .navigationTitle("Cubby")
            .sheet(isPresented: $showScanner) {
                DocumentScannerView { images in
                    scannedImages = images
                    showScanner = false
                }
            }
        }
    }
}

#Preview {
    ContentView()
}
