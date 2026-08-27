import Foundation

struct CubbyDocument: Identifiable, Codable {
    let id: UUID
    let filename: String
    let createdAt: Date
    let type: DocumentType

    enum DocumentType: String, Codable {
        case receipt
        case invoice
        case other
    }
}
