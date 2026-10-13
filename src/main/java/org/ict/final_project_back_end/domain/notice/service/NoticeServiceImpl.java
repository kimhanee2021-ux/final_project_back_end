package org.ict.final_project_back_end.domain.notice.service;

import org.ict.final_project_back_end.domain.notice.dto.*;
import org.ict.final_project_back_end.domain.notice.entity.Notice;
import org.ict.final_project_back_end.domain.notice.exception.*;
import org.ict.final_project_back_end.domain.notice.repository.NoticeRepository;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.exception.AuthenticationRequiredException;
import org.ict.final_project_back_end.global.exception.UserNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NoticeServiceImpl implements NoticeService {
  private final NoticeRepository notices;
  private final UserRepository users;

  public NoticeServiceImpl(NoticeRepository notices, UserRepository users) {
    this.notices = notices;
    this.users = users;
  }

  // 관리자 권한 체계 확정 전에는 기존 커뮤니티와 동일한 로그인/작성자 정책을 사용한다.
  @Override @Transactional
  public NoticeDetailResponse create(Long userId, NoticeRequest request) {
    requireLogin(userId);
    var author = users.findById(userId).orElseThrow(UserNotFoundException::new);
    return NoticeDetailResponse.from(notices.save(new Notice(request.title(), request.content(), author)));
  }

  @Override
  public NoticeDetailResponse find(Long id) {
    return NoticeDetailResponse.from(notices.findById(id).orElseThrow(NoticeNotFoundException::new));
  }

  @Override
  public NoticePageResponse list(int page, int size) {
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    return NoticePageResponse.from(notices.findAll(pageable).map(NoticeSummaryResponse::from));
  }

  @Override @Transactional
  public NoticeDetailResponse update(Long userId, Long id, NoticeRequest request) {
    var notice = ownedNotice(userId, id);
    notice.update(request.title(), request.content());
    notices.flush();
    return NoticeDetailResponse.from(notice);
  }

  @Override @Transactional
  public void delete(Long userId, Long id) {
    notices.delete(ownedNotice(userId, id));
    notices.flush();
  }

  private Notice ownedNotice(Long userId, Long id) {
    requireLogin(userId);
    var notice = notices.findById(id).orElseThrow(NoticeNotFoundException::new);
    if (notice.getAuthor() == null || !notice.getAuthor().getId().equals(userId)) throw new NoticeForbiddenException();
    return notice;
  }

  private void requireLogin(Long userId) {
    if (userId == null || userId <= 0) throw new AuthenticationRequiredException();
  }
}
