package com.loopers.application.favorite;

import com.loopers.domain.event.FavoriteAddedEvent;
import com.loopers.domain.event.FavoriteRemovedEvent;
import com.loopers.domain.favorite.model.FavoriteCommand;
import com.loopers.domain.favorite.service.FavoriteService;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class FavoriteFacade {

    private final FavoriteService favoriteService;
    private final MemberService memberService;
    private final ProductService productService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(rollbackFor = {Exception.class})
    public void addFavorite(String loginId, String password, Long productId) {
        Member member = memberService.findMember(loginId, password);
        Product product = productService.findProduct(productId);
        FavoriteCommand.Add command = new FavoriteCommand.Add(member.getId(), product.getId());
        boolean added = favoriteService.addFavorite(command);
        if (added) {
            eventPublisher.publishEvent(new FavoriteAddedEvent(product.getId(), member.getId()));
        }
    }

    @Transactional(rollbackFor = {Exception.class})
    public void deleteFavorite(String loginId, String password, Long productId) {
        Member member = memberService.findMember(loginId, password);
        Product product = productService.findProduct(productId);
        FavoriteCommand.Delete command = new FavoriteCommand.Delete(member.getId(), product.getId());
        favoriteService.delete(command);
        eventPublisher.publishEvent(new FavoriteRemovedEvent(product.getId(), member.getId()));
    }
}
