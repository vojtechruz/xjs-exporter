/**
 * Navigation panel collapsible sections functionality
 */
document.addEventListener('DOMContentLoaded', function() {
    // Get all collapsible section headers
    const collapsibleHeaders = document.querySelectorAll('.collapsible-header');
    
    // Initialize sections based on stored state
    initializeCollapsibleSections();
    
    // Add click event listeners to all collapsible headers
    collapsibleHeaders.forEach(header => {
        header.addEventListener('click', function() {
            // Toggle the collapsed class on the parent section
            const section = this.parentElement;
            section.classList.toggle('collapsed');
            
            // Store the current state in localStorage
            saveCollapsibleState();
        });
    });
    
    /**
     * Initialize collapsible sections based on stored state
     */
    function initializeCollapsibleSections() {
        // Get stored state from localStorage
        const storedState = localStorage.getItem('navigationCollapsibleState');
        
        if (storedState) {
            try {
                const state = JSON.parse(storedState);
                
                // Apply stored state to sections
                collapsibleHeaders.forEach(header => {
                    const section = header.parentElement;
                    const sectionId = section.getAttribute('data-section-id');
                    
                    // If section should be collapsed according to stored state
                    if (state[sectionId] === true) {
                        section.classList.add('collapsed');
                    } else {
                        section.classList.remove('collapsed');
                    }
                });
            } catch (e) {
                console.error('Error parsing stored navigation state:', e);
                // If there's an error, clear the stored state
                localStorage.removeItem('navigationCollapsibleState');
            }
        }
    }
    
    /**
     * Save the current collapsible state to localStorage
     */
    function saveCollapsibleState() {
        const state = {};
        
        // Get the state of all collapsible sections
        collapsibleHeaders.forEach(header => {
            const section = header.parentElement;
            const sectionId = section.getAttribute('data-section-id');
            state[sectionId] = section.classList.contains('collapsed');
        });
        
        // Save to localStorage
        localStorage.setItem('navigationCollapsibleState', JSON.stringify(state));
    }
    
    /**
     * Simple Lightbox Gallery Functionality
     */
    // Initialize image gallery lightbox
    initializeImageGallery();
    
    function initializeImageGallery() {
        // Get all gallery links
        const galleryLinks = document.querySelectorAll('.gallery-link');
        
        if (galleryLinks.length === 0) {
            return; // No gallery links found
        }
        
        // Create lightbox container
        const lightbox = document.createElement('div');
        lightbox.className = 'lightbox';
        lightbox.innerHTML = `
            <div class="lightbox-content">
                <button class="lightbox-close">&times;</button>
                <button class="lightbox-prev">&#10094;</button>
                <button class="lightbox-next">&#10095;</button>
                <div class="lightbox-image-container">
                    <img class="lightbox-image" src="" alt="">
                </div>
                <div class="lightbox-caption"></div>
                <div class="lightbox-counter"></div>
            </div>
        `;
        document.body.appendChild(lightbox);
        
        // Get lightbox elements
        const lightboxContent = lightbox.querySelector('.lightbox-content');
        const lightboxImage = lightbox.querySelector('.lightbox-image');
        const lightboxCaption = lightbox.querySelector('.lightbox-caption');
        const lightboxCounter = lightbox.querySelector('.lightbox-counter');
        const lightboxClose = lightbox.querySelector('.lightbox-close');
        const lightboxPrev = lightbox.querySelector('.lightbox-prev');
        const lightboxNext = lightbox.querySelector('.lightbox-next');
        
        // Gallery state
        let currentIndex = 0;
        const galleryItems = Array.from(galleryLinks);
        
        // Add click event to gallery links
        galleryLinks.forEach((link, index) => {
            link.addEventListener('click', function(e) {
                e.preventDefault();
                openLightbox(index);
            });
        });
        
        // Open lightbox with specific image
        function openLightbox(index) {
            if (index < 0 || index >= galleryItems.length) {
                return;
            }
            
            currentIndex = index;
            const link = galleryItems[currentIndex];
            const imageSrc = link.getAttribute('href');
            const imageTitle = link.getAttribute('data-title') || '';
            
            lightboxImage.src = imageSrc;
            lightboxCaption.textContent = imageTitle;
            lightboxCounter.textContent = `${currentIndex + 1} / ${galleryItems.length}`;
            
            lightbox.classList.add('active');
            document.body.style.overflow = 'hidden'; // Prevent scrolling
            
            updateNavButtons();
        }
        
        // Close lightbox
        function closeLightbox() {
            lightbox.classList.remove('active');
            document.body.style.overflow = ''; // Restore scrolling
        }
        
        // Navigate to previous image
        function prevImage() {
            openLightbox(currentIndex - 1);
        }
        
        // Navigate to next image
        function nextImage() {
            openLightbox(currentIndex + 1);
        }
        
        // Update navigation buttons visibility
        function updateNavButtons() {
            lightboxPrev.style.display = currentIndex > 0 ? 'block' : 'none';
            lightboxNext.style.display = currentIndex < galleryItems.length - 1 ? 'block' : 'none';
        }
        
        // Event listeners for lightbox controls
        lightboxClose.addEventListener('click', closeLightbox);
        lightboxPrev.addEventListener('click', prevImage);
        lightboxNext.addEventListener('click', nextImage);
        
        // Close lightbox when clicking outside the image
        lightbox.addEventListener('click', function(e) {
            if (e.target === lightbox) {
                closeLightbox();
            }
        });
        
        // Keyboard navigation
        document.addEventListener('keydown', function(e) {
            if (!lightbox.classList.contains('active')) {
                return;
            }
            
            if (e.key === 'Escape') {
                closeLightbox();
            } else if (e.key === 'ArrowLeft') {
                prevImage();
            } else if (e.key === 'ArrowRight') {
                nextImage();
            }
        });
    }
});