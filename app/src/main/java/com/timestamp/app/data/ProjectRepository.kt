package com.timestamp.app.data

import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val dao: ProjectDao) {
    val projects: Flow<List<ProjectEntity>> = dao.observeAll()
    suspend fun get(id: Long): ProjectEntity? = dao.get(id)
    suspend fun upsert(project: ProjectEntity): Long = dao.upsert(project)
    suspend fun delete(id: Long) = dao.deleteById(id)
}
