import Foundation
import SwiftUI
import Combine

final class DocumentRepository: ObservableObject {

    static let shared = DocumentRepository()

    @Published private(set) var documents: [CubbyDocument] = []

    private let metadataFilename = "documents.json"

    private init() {
        load()
    }

    func add(_ document: CubbyDocument) {
        documents.insert(document, at: 0)
        save()
    }

    private func metadataURL() -> URL {
        FileManager.default
            .urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent(metadataFilename)
    }

    private func save() {
        do {
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]

            let data = try encoder.encode(documents)

            try data.write(
                to: metadataURL(),
                options: .atomic
            )

            print("Saved document metadata.")
        } catch {
            print("Failed to save document metadata: \(error)")
        }
    }

    private func load() {
        let url = metadataURL()

        guard FileManager.default.fileExists(atPath: url.path) else {
            documents = []
            return
        }

        do {
            let data = try Data(contentsOf: url)

            documents = try JSONDecoder()
                .decode([CubbyDocument].self, from: data)

            documents.sort {
                $0.createdAt > $1.createdAt
            }

            print("Loaded \(documents.count) saved documents.")
        } catch {
            print("Failed to load document metadata: \(error)")
            documents = []
        }
    }
}
