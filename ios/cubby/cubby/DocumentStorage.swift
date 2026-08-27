import UIKit
import Foundation

final class DocumentStorage {

    static let shared = DocumentStorage()

    private init() {}

    func saveReceipt(_ image: UIImage) throws -> CubbyDocument {

        let id = UUID()

        let filename = "\(id.uuidString).jpg"

        guard let data = image.jpegData(compressionQuality: 0.9) else {
            throw StorageError.imageConversionFailed
        }

        let fileURL = documentsDirectory()
            .appendingPathComponent(filename)

        try data.write(to: fileURL)

        return CubbyDocument(
            id: id,
            filename: filename,
            createdAt: Date(),
            type: .receipt
        )
    }

    func loadImage(for document: CubbyDocument) -> UIImage? {

        let fileURL = documentsDirectory()
            .appendingPathComponent(document.filename)

        return UIImage(contentsOfFile: fileURL.path)
    }

    private func documentsDirectory() -> URL {
        FileManager.default.urls(
            for: .documentDirectory,
            in: .userDomainMask
        )[0]
    }
}

enum StorageError: Error {
    case imageConversionFailed
}
