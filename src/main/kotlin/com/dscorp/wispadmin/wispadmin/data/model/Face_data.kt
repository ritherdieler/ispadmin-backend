package com.dscorp.wispadmin.wispadmin.data.model

import java.util.Date
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.Lob
import javax.persistence.OneToOne
import javax.persistence.Table

@Entity
@Table(name = "face_data")
data class Face_data (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id:Int = 0,
    @Lob
    @Column(name = "face_embedding", columnDefinition = "LONGTEXT", nullable = false)
    var faceEmbedding: String,
    @Column(name = "image_url")
    var imageUrl: String? = null,
    var createdAt: Date = Date(),
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",nullable = false)
    var user: User
) {
    override fun toString(): String {
        return "Face_data(id=$id, imageUrl=$imageUrl, createdAt=$createdAt, userId=${user.id})"
    }
}

//Id
//User_id
//• Action (check-in / check-out)
//• Timestamp
//• Divice_info
