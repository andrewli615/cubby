import SwiftUI
import UIKit

struct ContentView: View {
    @StateObject private var repository = DocumentRepository.shared
    @State private var showScanner = false
    @State private var scannedImages: [UIImage] = []

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                if repository.documents.isEmpty {

                    VStack(spacing: 12) {
                        Image(systemName: "doc.viewfinder")
                            .font(.system(size: 60))

                        Text("No receipts yet")
                            .foregroundStyle(.secondary)
                    }

                } else {

                    ScrollView {
                        LazyVStack(spacing: 12) {

                            ForEach(repository.documents) { document in

                                HStack(spacing: 16) {

                                    if let image =
                                        DocumentStorage.shared.loadImage(for: document) {

                                        Image(uiImage: image)
                                            .resizable()
                                            .scaledToFill()
                                            .frame(width: 70, height: 90)
                                            .clipShape(
                                                RoundedRectangle(
                                                    cornerRadius: 8
                                                )
                                            )
                                    }

                                    VStack(alignment: .leading, spacing: 6) {

                                        Text("Receipt")
                                            .font(.headline)

                                        Text(
                                            document.createdAt.formatted(
                                                date: .abbreviated,
                                                time: .shortened
                                            )
                                        )
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)

                                        Text(document.filename)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }

                                    Spacer()
                                }
                                .padding()
                            }
                        }
                    }
                }

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
                    if let firstImage = images.first {
                        do {
                            let document = try DocumentStorage.shared.saveReceipt(firstImage)

                            repository.add(document)

                            print("Saved document:")
                            print(document)
                        } catch {
                            print("Failed to save receipt: \(error)")
                        }
                    }
                    showScanner = false
                }
            }
        }
    }
}

#Preview {
    ContentView()
}
